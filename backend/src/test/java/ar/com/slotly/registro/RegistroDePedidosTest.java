package ar.com.slotly.registro;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.http.Cookie;

import ar.com.slotly.BaseDeTest;
import ar.com.slotly.IntegrationTest;
import ar.com.slotly.panel.PanelDeTest;
import ar.com.slotly.seguridad.IntentosDeIngreso;

// Logs (Etapa 9): cada pedido a la API deja una línea con su id, el local y, en el panel, el usuario.
@IntegrationTest
class RegistroDePedidosTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PasswordEncoder encoder;

    @Autowired
    IntentosDeIngreso intentos;

    // Junta lo que registra el filtro durante cada test.
    final ListAppender<ILoggingEvent> registrado = new ListAppender<>();
    final Logger logger = (Logger) LoggerFactory.getLogger(RegistroDePedidos.class);

    UUID usuarioId;

    @BeforeEach
    void preparar() {
        intentos.reiniciar();
        BaseDeTest.vaciar(jdbc);
        jdbc.update("INSERT INTO local (nombre, subdominio) VALUES ('Demo', 'demo')");
        usuarioId = jdbc.queryForObject("""
                INSERT INTO usuario (local_id, email, password_hash, rol) SELECT id, 'duenio@demo.com', ?, 'admin' FROM local
                RETURNING id
                """, UUID.class, encoder.encode("clave"));
        registrado.start();
        logger.addAppender(registrado);
    }

    @AfterEach
    void soltar() {
        logger.detachAppender(registrado);
    }

    @Test
    @DisplayName("Etapa 9: un pedido a la API deja una línea con método, ruta, estado e id; el id vuelve en el header")
    void unaLineaPorPedido() throws Exception {
        String id = mockMvc.perform(get("/api/publico/local").header(HttpHeaders.HOST, "demo.localhost:5173"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader(RegistroDePedidos.HEADER);

        assertThat(id).matches("[0-9a-f]{8}");
        ILoggingEvent linea = unica();
        assertThat(linea.getFormattedMessage()).matches("GET /api/publico/local → 200 en \\d+ ms");
        assertThat(linea.getMDCPropertyMap())
                .containsEntry(RegistroDePedidos.PEDIDO, id)
                .containsEntry(RegistroDePedidos.LOCAL, "demo.localhost")
                .doesNotContainKey(RegistroDePedidos.USUARIO);
    }

    @Test
    @DisplayName("Etapa 9: en el panel, la línea lleva el id del usuario (no su mail); un 401 también queda registrado")
    void usuarioDelPanel() throws Exception {
        Cookie sesion = PanelDeTest.ingresar(mockMvc, "demo", "duenio@demo.com", "clave");
        registrado.list.clear();

        mockMvc.perform(get("/api/panel/yo").header(HttpHeaders.HOST, "demo.localhost").cookie(sesion))
                .andExpect(status().isOk());
        assertThat(unica().getMDCPropertyMap()).containsEntry(RegistroDePedidos.USUARIO, usuarioId.toString());
        registrado.list.clear();

        mockMvc.perform(get("/api/panel/yo").header(HttpHeaders.HOST, "demo.localhost"))
                .andExpect(status().isUnauthorized());
        assertThat(unica().getFormattedMessage()).startsWith("GET /api/panel/yo → 401");
        // Al terminar cada pedido el MDC queda limpio: nada pasa al siguiente.
        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }

    @Test
    @DisplayName("Etapa 9: un id que manda el proxy se reusa si es razonable; si no, se genera otro")
    void idDelProxy() throws Exception {
        mockMvc.perform(get("/api/publico/local").header(HttpHeaders.HOST, "demo.localhost")
                        .header(RegistroDePedidos.HEADER, "rndr-1234abcd"))
                .andExpect(header().string(RegistroDePedidos.HEADER, "rndr-1234abcd"));

        mockMvc.perform(get("/api/publico/local").header(HttpHeaders.HOST, "demo.localhost")
                        .header(RegistroDePedidos.HEADER, "<script>alert(1)</script>"))
                .andExpect(header().string(RegistroDePedidos.HEADER, matchesPattern("[0-9a-f]{8}")));
    }

    @Test
    @DisplayName("Etapa 9: el health (lo pide la plataforma cada pocos segundos) y las páginas del front no llenan el log")
    void loQueNoSeRegistra() throws Exception {
        mockMvc.perform(get("/api/health")).andExpect(status().isOk());
        mockMvc.perform(get("/agenda").header(HttpHeaders.HOST, "demo.localhost")).andExpect(status().isOk());

        assertThat(registrado.list).isEmpty();
    }

    private ILoggingEvent unica() {
        List<ILoggingEvent> lineas = registrado.list;
        assertThat(lineas).hasSize(1);
        return lineas.get(0);
    }
}
