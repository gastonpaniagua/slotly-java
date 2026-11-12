package ar.com.slotly.seguridad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import ar.com.slotly.BaseDeTest;
import ar.com.slotly.IntegrationTest;
import ar.com.slotly.RelojDeTest;

// HU-15: más de 10 pedidos de reserva por minuto desde la misma IP reciben 429, contra la app completa.
// Reloj fijo: lunes 16/11/2026 a las 9:00:30 en Buenos Aires.
@IntegrationTest
class LimitePorIpTest {

    private static final Instant AHORA = Instant.parse("2026-11-16T12:00:30Z");

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    RelojDeTest reloj;

    @Autowired
    LimiteDePedidos limite;

    UUID corteId;
    UUID anaId;

    @BeforeEach
    void preparar() {
        reloj.fijar(AHORA);
        limite.reiniciar();
        BaseDeTest.vaciar(jdbc);
        UUID demoId = jdbc.queryForObject("INSERT INTO local (nombre, subdominio) VALUES ('Demo', 'demo') RETURNING id", UUID.class);
        corteId = jdbc.queryForObject(
                "INSERT INTO servicio (local_id, nombre, duracion_min, precio) VALUES (?, 'Corte', 30, 8000) RETURNING id",
                UUID.class, demoId);
        anaId = jdbc.queryForObject("INSERT INTO profesional (local_id, nombre) VALUES (?, 'Ana') RETURNING id",
                UUID.class, demoId);
        jdbc.update("INSERT INTO profesional_servicio (local_id, profesional_id, servicio_id) VALUES (?, ?, ?)",
                demoId, anaId, corteId);
        jdbc.update("""
                INSERT INTO horario (local_id, profesional_id, dia_semana, hora_inicio, hora_fin) VALUES (?, ?, 2, '10:00', '13:00')
                """, demoId, anaId);
    }

    @Test
    @DisplayName("HU-15 CA1: el pedido 11 del minuto desde la misma IP recibe 429 y no se crea nada")
    void elOnceRecibe429() throws Exception {
        // 10 pedidos que no crean nada (hora fuera del horario): todos cuentan igual.
        for (int i = 0; i < LimiteDePedidos.MAXIMO_POR_MINUTO; i++) {
            apartar("14:00", "10.0.0.1").andExpect(status().isConflict());
        }

        // El 11, aunque sea válido, no pasa.
        apartar("10:00", "10.0.0.1")
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "30"))
                .andExpect(jsonPath("$.codigo").value("DEMASIADOS_INTENTOS"))
                .andExpect(jsonPath("$.detail").value("Demasiados intentos, esperá un minuto."));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM turno", Integer.class)).isZero();
    }

    @Test
    @DisplayName("HU-15 CA2: cuando pasa el minuto, puede volver a intentar")
    void pasaElMinuto() throws Exception {
        for (int i = 0; i <= LimiteDePedidos.MAXIMO_POR_MINUTO; i++) {
            apartar("14:00", "10.0.0.1");
        }
        apartar("10:00", "10.0.0.1").andExpect(status().isTooManyRequests());

        reloj.fijar(AHORA.plusSeconds(30));   // 9:01:00: minuto nuevo

        apartar("10:00", "10.0.0.1").andExpect(status().isCreated());
    }

    @Test
    @DisplayName("HU-15: el límite es por IP; otra IP no se ve afectada")
    void otraIp() throws Exception {
        for (int i = 0; i <= LimiteDePedidos.MAXIMO_POR_MINUTO; i++) {
            apartar("14:00", "10.0.0.1");
        }

        apartar("10:00", "10.0.0.2").andExpect(status().isCreated());
    }

    @Test
    @DisplayName("HU-15: confirmar también cuenta; ver la disponibilidad (GET) no")
    void queCuentaYQueNo() throws Exception {
        for (int i = 0; i < 20; i++) {
            mockMvc.perform(get("/api/publico/disponibilidad/horas")
                            .header(HttpHeaders.HOST, "demo.localhost")
                            .param("servicios", corteId.toString())
                            .param("profesional", anaId.toString())
                            .param("fecha", "2026-11-17")
                            .with(r -> { r.setRemoteAddr("10.0.0.1"); return r; }))
                    .andExpect(status().isOk());
        }
        for (int i = 0; i < LimiteDePedidos.MAXIMO_POR_MINUTO; i++) {
            mockMvc.perform(post("/api/publico/apartados/" + UUID.randomUUID() + "/confirmar")
                    .header(HttpHeaders.HOST, "demo.localhost")
                    .contentType(MediaType.APPLICATION_JSON).content("{}")
                    .with(r -> { r.setRemoteAddr("10.0.0.1"); return r; }));
        }

        apartar("10:00", "10.0.0.1").andExpect(status().isTooManyRequests());
    }

    private ResultActions apartar(String hora, String ip) throws Exception {
        return mockMvc.perform(post("/api/publico/apartados")
                .header(HttpHeaders.HOST, "demo.localhost")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"servicios\": [\"" + corteId + "\"], \"profesional\": \"" + anaId
                        + "\", \"fecha\": \"2026-11-17\", \"hora\": \"" + hora + "\"}")
                .with(r -> {
                    r.setRemoteAddr(ip);
                    return r;
                }));
    }
}
