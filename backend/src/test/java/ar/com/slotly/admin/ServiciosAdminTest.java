package ar.com.slotly.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import jakarta.servlet.http.Cookie;

import ar.com.slotly.BaseDeTest;
import ar.com.slotly.IntegrationTest;
import ar.com.slotly.panel.PanelDeTest;
import ar.com.slotly.seguridad.IntentosDeIngreso;

// HU-24: administrar servicios desde el panel, contra la app completa.
@IntegrationTest
class ServiciosAdminTest {

    private static final String CLAVE = "clave";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PasswordEncoder encoder;

    @Autowired
    IntentosDeIngreso intentos;

    UUID demoId;
    UUID corteId;
    UUID barbaId;
    Cookie admin;
    Cookie profesional;

    @BeforeEach
    void preparar() throws Exception {
        intentos.reiniciar();
        BaseDeTest.vaciar(jdbc);
        demoId = jdbc.queryForObject("INSERT INTO local (nombre, subdominio) VALUES ('Demo', 'demo') RETURNING id", UUID.class);
        corteId = jdbc.queryForObject("""
                INSERT INTO servicio (local_id, nombre, duracion_min, precio, orden) VALUES (?, 'Corte', 30, 8000, 1) RETURNING id
                """, UUID.class, demoId);
        barbaId = jdbc.queryForObject("""
                INSERT INTO servicio (local_id, nombre, duracion_min, precio, orden) VALUES (?, 'Barba', 20, 5000, 2) RETURNING id
                """, UUID.class, demoId);
        UUID anaId = jdbc.queryForObject("INSERT INTO profesional (local_id, nombre) VALUES (?, 'Ana') RETURNING id",
                UUID.class, demoId);
        jdbc.update("INSERT INTO usuario (local_id, email, password_hash, rol) VALUES (?, 'duenio@demo.com', ?, 'admin')",
                demoId, encoder.encode(CLAVE));
        jdbc.update("INSERT INTO usuario (local_id, email, password_hash, rol, profesional_id) VALUES (?, 'ana@demo.com', ?, 'profesional', ?)",
                demoId, encoder.encode(CLAVE), anaId);
        admin = PanelDeTest.ingresar(mockMvc, "demo", "duenio@demo.com", CLAVE);
        profesional = PanelDeTest.ingresar(mockMvc, "demo", "ana@demo.com", CLAVE);
    }

    @Test
    @DisplayName("HU-24 CA1: un servicio nuevo aparece en la lista del panel (último) y en la home pública")
    void crear() throws Exception {
        enviar(post("/api/admin/servicios"), admin, """
                {"nombre": " Color ", "descripcion": "Coloración completa.", "duracionMin": 90, "precio": 30000,
                 "limpiezaMin": 10, "activo": true}
                """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nombre").value("Color"))
                .andExpect(jsonPath("$.orden").value(3))
                .andExpect(jsonPath("$.limpiezaMin").value(10));

        listar(admin).andExpect(jsonPath("$[*].nombre", contains("Corte", "Barba", "Color")));
        homePublica().andExpect(jsonPath("$.servicios[*].nombre", hasItem("Color")));
    }

    @Test
    @DisplayName("HU-24 CA2: cambiar precio y duración no toca lo copiado en los turnos ya reservados")
    void editarNoTocaLosTurnos() throws Exception {
        UUID turno = turnoCon(corteId);

        enviar(put("/api/admin/servicios/" + corteId), admin, """
                {"nombre": "Corte", "duracionMin": 45, "precio": 9500, "limpiezaMin": null, "activo": true}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.precio").value(9500))
                .andExpect(jsonPath("$.duracionMin").value(45));

        assertThat(jdbc.queryForList("SELECT precio FROM turno_servicio WHERE turno_id = ?", Integer.class, turno))
                .containsExactly(8000);
        assertThat(jdbc.queryForList("SELECT duracion_min FROM turno_servicio WHERE turno_id = ?", Integer.class, turno))
                .containsExactly(30);
    }

    @Test
    @DisplayName("HU-24 CA3: un servicio oculto deja de ofrecerse en la web pública y sus turnos siguen igual")
    void ocultar() throws Exception {
        UUID turno = turnoCon(barbaId);

        enviar(put("/api/admin/servicios/" + barbaId), admin, """
                {"nombre": "Barba", "duracionMin": 20, "precio": 5000, "activo": false}
                """)
                .andExpect(jsonPath("$.activo").value(false));

        homePublica().andExpect(jsonPath("$.servicios[*].nombre", not(hasItem("Barba"))));
        listar(admin).andExpect(jsonPath("$[*].nombre", contains("Corte", "Barba")));   // el panel lo sigue viendo
        assertThat(jdbc.queryForObject("SELECT estado FROM turno WHERE id = ?", String.class, turno)).isEqualTo("confirmado");
    }

    @Test
    @DisplayName("HU-24 CA4: datos inválidos responden 400 con el error de cada campo")
    void datosInvalidos() throws Exception {
        enviar(post("/api/admin/servicios"), admin, """
                {"nombre": "", "duracionMin": 0, "precio": -1, "limpiezaMin": 31, "activo": true}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.errores[*].campo", containsInAnyOrder("nombre", "duracionMin", "precio", "limpiezaMin")));

        enviar(post("/api/admin/servicios"), admin, """
                {"nombre": "Raro", "duracionMin": 42, "precio": 1000, "activo": true}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("duracionMin"))
                .andExpect(jsonPath("$.errores[0].mensaje").value("La duración tiene que ser múltiplo de 5 minutos."));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM servicio", Integer.class)).isEqualTo(2);
    }

    @Test
    @DisplayName("HU-24 CA5: un profesional no puede administrar servicios (403)")
    void profesionalNo() throws Exception {
        listar(profesional).andExpect(status().isForbidden()).andExpect(jsonPath("$.codigo").value("SIN_PERMISO"));
        enviar(post("/api/admin/servicios"), profesional, """
                {"nombre": "Color", "duracionMin": 90, "precio": 30000, "activo": true}
                """)
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("HU-24: el orden de la lista se cambia y la home pública lo respeta")
    void ordenar() throws Exception {
        enviar(post("/api/admin/servicios/orden"), admin, "{\"ids\": [\"" + barbaId + "\", \"" + corteId + "\"]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].nombre", contains("Barba", "Corte")));

        homePublica().andExpect(jsonPath("$.servicios[*].nombre", contains("Barba", "Corte")));
        // Un orden que no tiene a todos (o repite) se rechaza.
        enviar(post("/api/admin/servicios/orden"), admin, "{\"ids\": [\"" + barbaId + "\", \"" + barbaId + "\"]}")
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("HU-04: el servicio de otro local no se puede editar desde este panel (404)")
    void servicioDeOtroLocal() throws Exception {
        UUID otraId = jdbc.queryForObject("INSERT INTO local (nombre, subdominio) VALUES ('Otra', 'otra') RETURNING id", UUID.class);
        UUID deOtra = jdbc.queryForObject("""
                INSERT INTO servicio (local_id, nombre, duracion_min, precio) VALUES (?, 'Ajeno', 30, 1000) RETURNING id
                """, UUID.class, otraId);

        enviar(put("/api/admin/servicios/" + deOtra), admin, """
                {"nombre": "Robado", "duracionMin": 30, "precio": 1, "activo": true}
                """)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("SERVICIO_NO_ENCONTRADO"));
        assertThat(jdbc.queryForObject("SELECT nombre FROM servicio WHERE id = ?", String.class, deOtra)).isEqualTo("Ajeno");
    }

    private ResultActions listar(Cookie sesion) throws Exception {
        return mockMvc.perform(get("/api/admin/servicios").header(HttpHeaders.HOST, "demo.localhost").cookie(sesion));
    }

    private ResultActions enviar(MockHttpServletRequestBuilder pedido, Cookie sesion, String cuerpo) throws Exception {
        return mockMvc.perform(pedido.header(HttpHeaders.HOST, "demo.localhost").cookie(sesion)
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo).with(PanelDeTest.conCsrf()));
    }

    private ResultActions homePublica() throws Exception {
        return mockMvc.perform(get("/api/publico/local").header(HttpHeaders.HOST, "demo.localhost"));
    }

    // Un bloqueo no lleva servicios; para tener precio copiado se usa un turno manual con cliente.
    private UUID turnoCon(UUID servicio) {
        UUID cliente = jdbc.queryForObject("INSERT INTO cliente (local_id, nombre) VALUES (?, 'Lucía') RETURNING id",
                UUID.class, demoId);
        UUID ana = jdbc.queryForObject("SELECT id FROM profesional WHERE local_id = ?", UUID.class, demoId);
        UUID turno = jdbc.queryForObject("""
                INSERT INTO turno (local_id, profesional_id, cliente_id, tipo, estado, inicio, fin, ocupado_hasta)
                VALUES (?, ?, ?, 'manual', 'confirmado', '2026-11-17T10:00-03:00', '2026-11-17T10:30-03:00', '2026-11-17T10:30-03:00')
                RETURNING id
                """, UUID.class, demoId, ana, cliente);
        Integer[] copia = jdbc.queryForObject("SELECT duracion_min, precio FROM servicio WHERE id = ?",
                (fila, n) -> new Integer[] { fila.getInt(1), fila.getInt(2) }, servicio);
        jdbc.update("INSERT INTO turno_servicio (local_id, turno_id, servicio_id, duracion_min, precio) VALUES (?, ?, ?, ?, ?)",
                demoId, turno, servicio, copia[0], copia[1]);
        return turno;
    }
}
