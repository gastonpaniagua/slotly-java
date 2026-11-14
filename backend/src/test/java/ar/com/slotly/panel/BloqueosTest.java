package ar.com.slotly.panel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

import jakarta.servlet.http.Cookie;

import ar.com.slotly.BaseDeTest;
import ar.com.slotly.IntegrationTest;
import ar.com.slotly.RelojDeTest;
import ar.com.slotly.seguridad.IntentosDeIngreso;

// HU-27: bloqueos, feriados y vacaciones desde la agenda, contra la app completa.
// Reloj: lunes 16/11/2026 a las 9:00 en Buenos Aires. Ana y Bruno hacen Corte (30 min) y trabajan el martes de 10 a 20.
@IntegrationTest
class BloqueosTest {

    private static final String CLAVE = "clave";
    private static final Instant LUNES_9 = Instant.parse("2026-11-16T12:00:00Z");

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PasswordEncoder encoder;

    @Autowired
    IntentosDeIngreso intentos;

    @Autowired
    RelojDeTest reloj;

    UUID demoId;
    UUID anaId;
    UUID brunoId;
    UUID corteId;
    Cookie admin;
    Cookie ana;

    @BeforeEach
    void preparar() throws Exception {
        reloj.fijar(LUNES_9);
        intentos.reiniciar();
        BaseDeTest.vaciar(jdbc);
        demoId = jdbc.queryForObject("INSERT INTO local (nombre, subdominio) VALUES ('Demo', 'demo') RETURNING id", UUID.class);
        corteId = jdbc.queryForObject(
                "INSERT INTO servicio (local_id, nombre, duracion_min, precio) VALUES (?, 'Corte', 30, 8000) RETURNING id",
                UUID.class, demoId);
        anaId = profesional("Ana", 1);
        brunoId = profesional("Bruno", 2);
        jdbc.update("INSERT INTO usuario (local_id, email, password_hash, rol) VALUES (?, 'duenio@demo.com', ?, 'admin')",
                demoId, encoder.encode(CLAVE));
        jdbc.update("INSERT INTO usuario (local_id, email, password_hash, rol, profesional_id) VALUES (?, 'ana@demo.com', ?, 'profesional', ?)",
                demoId, encoder.encode(CLAVE), anaId);
        admin = PanelDeTest.ingresar(mockMvc, "demo", "duenio@demo.com", CLAVE);
        ana = PanelDeTest.ingresar(mockMvc, "demo", "ana@demo.com", CLAVE);
    }

    @Test
    @DisplayName("HU-27 CA1: un bloqueo de horas de un profesional saca esas horas de la reserva, y solo las de él")
    void bloquearHoras() throws Exception {
        bloquear(admin, anaId.toString(), "2026-11-17T10:00", "2026-11-17T12:00", "Médico")
                .andExpect(status().isCreated());

        horas(anaId).andExpect(jsonPath("$.horas", not(hasItem("10:00"))))
                .andExpect(jsonPath("$.horas", not(hasItem("11:30"))))
                .andExpect(jsonPath("$.horas", hasItem("12:00")));
        horas(brunoId).andExpect(jsonPath("$.horas", hasItem("10:00")));
        mockMvc.perform(get("/api/panel/agenda").header(HttpHeaders.HOST, "demo.localhost").cookie(admin)
                        .param("desde", "2026-11-17").param("dias", "1").param("profesional", anaId.toString()))
                .andExpect(jsonPath("$.turnos[0].tipo").value("bloqueo"))
                .andExpect(jsonPath("$.turnos[0].motivo").value("Médico"));
    }

    @Test
    @DisplayName("HU-27 CA1: un feriado de todo el local (el día entero) deja ese día sin lugar para todos")
    void feriadoDeTodoElLocal() throws Exception {
        bloquear(admin, "todos", "2026-11-17T00:00", "2026-11-18T00:00", "Feriado")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ids.length()").value(2));

        for (UUID profesional : new UUID[] { anaId, brunoId }) {
            mockMvc.perform(get("/api/publico/disponibilidad/dias").header(HttpHeaders.HOST, "demo.localhost")
                            .param("servicios", corteId.toString()).param("profesional", profesional.toString())
                            .param("desde", "2026-11-16").param("hasta", "2026-11-24"))
                    .andExpect(jsonPath("$.dias[?(@.fecha == '2026-11-17')].hayLugar", contains(false)))
                    // Solo ese día: el martes siguiente sí tiene lugar.
                    .andExpect(jsonPath("$.dias[?(@.fecha == '2026-11-24')].hayLugar", contains(true)));
        }
    }

    @Test
    @DisplayName("HU-27 CA2: con turnos en el rango no se guarda: dice cuántos y cuáles")
    void turnosEnElRango() throws Exception {
        UUID turno = turnoDe(anaId, "10:30", "11:00");

        bloquear(admin, "todos", "2026-11-17T10:00", "2026-11-17T12:00", null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("TURNOS_EN_RANGO"))
                .andExpect(jsonPath("$.detail").value("Hay 1 turno en ese rango."))
                .andExpect(jsonPath("$.turnos[0].id").value(turno.toString()))
                .andExpect(jsonPath("$.turnos[0].profesionalNombre").value("Ana"))
                .andExpect(jsonPath("$.turnos[0].inicio").value("2026-11-17T10:30"))
                .andExpect(jsonPath("$.turnos[0].clienteNombre").value("Lucía"));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM turno WHERE tipo = 'bloqueo'", Integer.class)).isZero();
    }

    @Test
    @DisplayName("HU-27 CA3: un profesional bloquea su agenda, pero no la de otro ni la de todo el local")
    void profesionalSoloLaSuya() throws Exception {
        bloquear(ana, null, "2026-11-17T10:00", "2026-11-17T11:00", null).andExpect(status().isCreated());
        assertThat(jdbc.queryForObject("SELECT profesional_id FROM turno WHERE tipo = 'bloqueo'", UUID.class)).isEqualTo(anaId);

        bloquear(ana, brunoId.toString(), "2026-11-17T12:00", "2026-11-17T13:00", null)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("SIN_PERMISO"));
        bloquear(ana, "todos", "2026-11-17T12:00", "2026-11-17T13:00", null).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("HU-27 CA3 y CA4: al borrar un bloqueo las horas vuelven; un profesional no borra el de otro")
    void borrar() throws Exception {
        String deBruno = JsonPath.read(bloquear(admin, brunoId.toString(), "2026-11-17T10:00", "2026-11-17T12:00", null)
                .andReturn().getResponse().getContentAsString(), "$.ids[0]");

        borrar(ana, deBruno).andExpect(status().isNotFound());
        horas(brunoId).andExpect(jsonPath("$.horas", not(hasItem("10:00"))));

        borrar(admin, deBruno).andExpect(status().isNoContent());
        horas(brunoId).andExpect(jsonPath("$.horas", hasItem("10:00")));
    }

    @Test
    @DisplayName("HU-27: un bloqueo que termina antes de empezar se rechaza; uno que se pisa con otro bloqueo, también")
    void rangosInvalidos() throws Exception {
        bloquear(admin, anaId.toString(), "2026-11-17T12:00", "2026-11-17T10:00", null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("hasta"));

        bloquear(admin, anaId.toString(), "2026-11-17T10:00", "2026-11-17T12:00", null).andExpect(status().isCreated());
        bloquear(admin, anaId.toString(), "2026-11-17T11:00", "2026-11-17T13:00", null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("HORARIO_OCUPADO"));
    }

    @Test
    @DisplayName("HU-27: un apartado que ya venció (pero sigue como 'apartado') no impide el bloqueo")
    void apartadoVencidoNoImpide() throws Exception {
        jdbc.update("""
                INSERT INTO turno (local_id, profesional_id, tipo, estado, inicio, fin, ocupado_hasta, vence_en)
                VALUES (?, ?, 'reserva', 'apartado', '2026-11-17T10:00-03:00', '2026-11-17T10:30-03:00',
                        '2026-11-17T10:30-03:00', '2026-11-16T08:00-03:00')
                """, demoId, anaId);

        bloquear(admin, anaId.toString(), "2026-11-17T10:00", "2026-11-17T12:00", null).andExpect(status().isCreated());
    }

    private ResultActions bloquear(Cookie sesion, String profesional, String desde, String hasta, String motivo) throws Exception {
        String cuerpo = "{" + (profesional == null ? "" : "\"profesional\": \"" + profesional + "\", ")
                + "\"desde\": \"" + desde + "\", \"hasta\": \"" + hasta + "\""
                + (motivo == null ? "" : ", \"motivo\": \"" + motivo + "\"") + "}";
        return mockMvc.perform(post("/api/panel/bloqueos").header(HttpHeaders.HOST, "demo.localhost").cookie(sesion)
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo).with(PanelDeTest.conCsrf()));
    }

    private ResultActions borrar(Cookie sesion, String id) throws Exception {
        return mockMvc.perform(delete("/api/panel/bloqueos/" + id).header(HttpHeaders.HOST, "demo.localhost").cookie(sesion)
                .with(PanelDeTest.conCsrf()));
    }

    private ResultActions horas(UUID profesional) throws Exception {
        return mockMvc.perform(get("/api/publico/disponibilidad/horas").header(HttpHeaders.HOST, "demo.localhost")
                .param("servicios", corteId.toString()).param("profesional", profesional.toString()).param("fecha", "2026-11-17"));
    }

    private UUID profesional(String nombre, int orden) {
        UUID id = jdbc.queryForObject("INSERT INTO profesional (local_id, nombre, orden) VALUES (?, ?, ?) RETURNING id",
                UUID.class, demoId, nombre, orden);
        jdbc.update("INSERT INTO profesional_servicio (local_id, profesional_id, servicio_id) VALUES (?, ?, ?)", demoId, id, corteId);
        jdbc.update("INSERT INTO horario (local_id, profesional_id, dia_semana, hora_inicio, hora_fin) VALUES (?, ?, 2, '10:00', '20:00')",
                demoId, id);
        return id;
    }

    private UUID turnoDe(UUID profesional, String desde, String hasta) {
        UUID cliente = jdbc.queryForObject("INSERT INTO cliente (local_id, nombre) VALUES (?, 'Lucía') RETURNING id", UUID.class, demoId);
        return jdbc.queryForObject("""
                INSERT INTO turno (local_id, profesional_id, cliente_id, tipo, estado, inicio, fin, ocupado_hasta)
                VALUES (?, ?, ?, 'manual', 'confirmado', ?::timestamptz, ?::timestamptz, ?::timestamptz) RETURNING id
                """, UUID.class, demoId, profesional, cliente, "2026-11-17T" + desde + "-03:00", "2026-11-17T" + hasta + "-03:00",
                "2026-11-17T" + hasta + "-03:00");
    }
}
