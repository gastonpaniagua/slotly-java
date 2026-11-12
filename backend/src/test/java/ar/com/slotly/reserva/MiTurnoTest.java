package ar.com.slotly.reserva;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import ar.com.slotly.BaseDeTest;
import ar.com.slotly.IntegrationTest;
import ar.com.slotly.RelojDeTest;
import ar.com.slotly.local.Local;
import ar.com.slotly.local.LocalResolver;
import ar.com.slotly.reserva.ApartadoService.Apartado;
import ar.com.slotly.reserva.ConfirmacionService.DatosDelCliente;
import ar.com.slotly.seguridad.LimiteDePedidos;

// HU-20 (y en el paso 43, HU-21): lo que el cliente hace con su turno desde el link, contra la app completa.
// Reloj: lunes 16/11/2026 a las 9:00 en Buenos Aires. El turno: martes 17 a las 10:00 (13:00 UTC), con Ana.
@IntegrationTest
class MiTurnoTest {

    private static final LocalDate MARTES = LocalDate.of(2026, 11, 17);
    private static final Instant LUNES_9 = Instant.parse("2026-11-16T12:00:00Z");

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    RelojDeTest reloj;

    @Autowired
    LimiteDePedidos limite;

    @Autowired
    ApartadoService apartados;

    @Autowired
    ConfirmacionService confirmaciones;

    @Autowired
    LocalResolver localResolver;

    UUID corteId;
    UUID anaId;
    Apartado turno;

    @BeforeEach
    void preparar() {
        reloj.fijar(LUNES_9);
        limite.reiniciar();
        BaseDeTest.vaciar(jdbc);
        UUID demoId = jdbc.queryForObject("INSERT INTO local (nombre, subdominio) VALUES ('Demo', 'demo') RETURNING id", UUID.class);
        jdbc.queryForObject("INSERT INTO local (nombre, subdominio) VALUES ('Otra', 'otra') RETURNING id", UUID.class);
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
        turno = reservar("10:00");
    }

    @Test
    @DisplayName("HU-20 CA1: con más de 3 horas por delante se cancela y el horario vuelve a ofrecerse")
    void cancelar() throws Exception {
        horas().andExpect(jsonPath("$.horas", not(hasItem("10:00"))));

        cancelar("demo", turno.token())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("cancelado"))
                .andExpect(jsonPath("$.puedeCambiar").value(false));

        assertThat(estado()).isEqualTo("cancelado");
        horas().andExpect(jsonPath("$.horas", hasItem("10:00")));
    }

    @Test
    @DisplayName("HU-20 CA2: con 3 horas o menos responde 409 FUERA_DE_PLAZO y el turno sigue confirmado")
    void fueraDePlazo() throws Exception {
        reloj.fijar(Instant.parse("2026-11-17T10:00:00Z"));   // martes 7:00: faltan justo 3 horas

        cancelar("demo", turno.token())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("FUERA_DE_PLAZO"));

        assertThat(estado()).isEqualTo("confirmado");
    }

    @Test
    @DisplayName("HU-20 CA2: un minuto antes del límite todavía se puede")
    void justoAntesDelLimite() throws Exception {
        reloj.fijar(Instant.parse("2026-11-17T09:59:00Z"));

        cancelar("demo", turno.token()).andExpect(status().isOk());
    }

    @Test
    @DisplayName("HU-20 CA3: cancelar un turno ya cancelado no cambia nada y responde el turno cancelado")
    void cancelarDosVeces() throws Exception {
        cancelar("demo", turno.token()).andExpect(status().isOk());

        cancelar("demo", turno.token())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("cancelado"));
        assertThat(estado()).isEqualTo("cancelado");
    }

    @Test
    @DisplayName("HU-20: un turno que ya no está confirmado (por ejemplo, ya atendido) no se cancela desde el link")
    void soloConfirmados() throws Exception {
        jdbc.update("UPDATE turno SET estado = 'completado' WHERE id = ?", turno.id());

        cancelar("demo", turno.token())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("FUERA_DE_PLAZO"));
        assertThat(estado()).isEqualTo("completado");
    }

    @Test
    @DisplayName("HU-19 CA2 y CA5: con otro token o desde otro local, 404 y no se cancela nada")
    void tokenOLocalEquivocado() throws Exception {
        cancelar("demo", "inventado").andExpect(status().isNotFound());
        cancelar("otra", turno.token()).andExpect(status().isNotFound());

        assertThat(estado()).isEqualTo("confirmado");
    }

    @Test
    @DisplayName("HU-19 CA3: con el link vencido, 410")
    void linkVencido() throws Exception {
        jdbc.update("UPDATE turno SET token_vence_en = ? WHERE id = ?", Timestamp.from(LUNES_9), turno.id());

        cancelar("demo", turno.token())
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.codigo").value("LINK_VENCIDO"));
    }

    private Apartado reservar(String hora) {
        Local demo = localResolver.resolver("demo.localhost");
        Apartado apartado = apartados.apartar(demo, new ApartadoService.Pedido(Set.of(corteId), anaId.toString(),
                MARTES, LocalTime.parse(hora), null));
        confirmaciones.confirmar(demo, apartado.id(), apartado.token(),
                new DatosDelCliente("Lucía", "11 2345-6789", null, "efectivo"));
        return apartado;
    }

    private ResultActions cancelar(String subdominio, String token) throws Exception {
        return mockMvc.perform(post("/api/publico/turnos/" + turno.id() + "/cancelar")
                .header(HttpHeaders.HOST, subdominio + ".localhost")
                .header(ConfirmacionController.HEADER_TOKEN, token));
    }

    private ResultActions horas() throws Exception {
        return mockMvc.perform(get("/api/publico/disponibilidad/horas")
                .header(HttpHeaders.HOST, "demo.localhost")
                .param("servicios", corteId.toString())
                .param("profesional", anaId.toString())
                .param("fecha", MARTES.toString()));
    }

    private String estado() {
        return jdbc.queryForObject("SELECT estado FROM turno WHERE id = ?", String.class, turno.id());
    }
}
