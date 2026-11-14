package ar.com.slotly.panel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
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

// HU-28 y HU-29: turno manual, mover y cancelar desde la agenda, contra la app completa.
// Reloj: lunes 16/11/2026 a las 9:00 en Buenos Aires. Ana y Bruno hacen Corte (30 min) y trabajan el martes de 10 a 20.
@IntegrationTest
class TurnosPanelTest {

    private static final String CLAVE = "clave";

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
        reloj.fijar(Instant.parse("2026-11-16T12:00:00Z"));
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
    @DisplayName("HU-28 CA1: un turno manual con nombre (sin teléfono) deja de ofrecerse y aparece en la agenda, con el precio copiado")
    void turnoManual() throws Exception {
        String id = idDe(manual(admin, anaId.toString(), "2026-11-17", "10:00", "Pedro", null).andExpect(status().isCreated()));

        horas(anaId).andExpect(jsonPath("$.horas", not(hasItem("10:00"))));
        horas(brunoId).andExpect(jsonPath("$.horas", hasItem("10:00")));
        mockMvc.perform(get("/api/panel/agenda").header(HttpHeaders.HOST, "demo.localhost").cookie(admin)
                        .param("desde", "2026-11-17").param("dias", "1").param("profesional", anaId.toString()))
                .andExpect(jsonPath("$.turnos[0].id").value(id))
                .andExpect(jsonPath("$.turnos[0].tipo").value("manual"))
                .andExpect(jsonPath("$.turnos[0].clienteNombre").value("Pedro"))
                .andExpect(jsonPath("$.turnos[0].clienteTelefono").doesNotExist());
        assertThat(jdbc.queryForObject("SELECT precio FROM turno_servicio WHERE turno_id = ?::uuid", Integer.class, id)).isEqualTo(8000);
    }

    @Test
    @DisplayName("HU-28 CA1: con teléfono, usa el cliente que ya tiene ese teléfono en el local")
    void mismoCliente() throws Exception {
        UUID lucia = jdbc.queryForObject("INSERT INTO cliente (local_id, nombre, telefono) VALUES (?, 'Lucía', '+5491123456789') RETURNING id",
                UUID.class, demoId);

        String id = idDe(manual(admin, anaId.toString(), "2026-11-17", "10:00", "Lu", "11 2345-6789").andExpect(status().isCreated()));

        assertThat(jdbc.queryForObject("SELECT cliente_id FROM turno WHERE id = ?::uuid", UUID.class, id)).isEqualTo(lucia);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM cliente", Integer.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("HU-28 CA2: un turno manual que se pisa con otro turno o un bloqueo da HORARIO_OCUPADO y no se guarda nada")
    void manualOcupado() throws Exception {
        bloqueo(anaId, "10:00", "11:00");

        manual(admin, anaId.toString(), "2026-11-17", "10:30", "Pedro", null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("HORARIO_OCUPADO"));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM turno WHERE tipo = 'manual'", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM cliente", Integer.class)).isZero();
    }

    @Test
    @DisplayName("HU-28 CA3: el turno manual no cuenta el límite de turnos del cliente y se permite sin anticipación")
    void sinLimitesDelCliente() throws Exception {
        UUID lucia = jdbc.queryForObject("INSERT INTO cliente (local_id, nombre, telefono) VALUES (?, 'Lucía', '+5491123456789') RETURNING id",
                UUID.class, demoId);
        turno(brunoId, lucia, "12:00", "12:30");
        turno(brunoId, lucia, "13:00", "13:30");   // ya tiene 2 turnos futuros (el máximo para la web)

        // Lunes 9:15: dentro de la anticipación mínima (1 h) y fuera del horario. Desde el panel se puede igual.
        manual(admin, anaId.toString(), "2026-11-16", "09:15", "Lucía", "11 2345-6789").andExpect(status().isCreated());
    }

    @Test
    @DisplayName("HU-28: un profesional carga turnos en su agenda, pero no en la de otro")
    void profesionalSoloLaSuya() throws Exception {
        manual(ana, null, "2026-11-17", "10:00", "Pedro", null).andExpect(status().isCreated());
        manual(ana, brunoId.toString(), "2026-11-17", "10:00", "Pedro", null).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("HU-29 CA1: mover un turno lo deja en el horario nuevo y libera el viejo, con la misma duración")
    void mover() throws Exception {
        UUID turno = turno(anaId, null, "10:00", "10:30");

        mover(admin, turno, "2026-11-17", "11:00").andExpect(status().isOk());

        horas(anaId).andExpect(jsonPath("$.horas", hasItem("10:00"))).andExpect(jsonPath("$.horas", not(hasItem("11:00"))));
        assertThat(jdbc.queryForObject("SELECT fin - inicio FROM turno WHERE id = ?", String.class, turno)).isEqualTo("00:30:00");
        // Correrlo 15 minutos (se pisa consigo mismo) también se puede.
        mover(admin, turno, "2026-11-17", "11:15").andExpect(status().isOk());
    }

    @Test
    @DisplayName("HU-29 CA2: moverlo a un hueco ocupado da HORARIO_OCUPADO y el turno no cambia")
    void moverAOcupado() throws Exception {
        UUID turno = turno(anaId, null, "10:00", "10:30");
        bloqueo(anaId, "12:00", "13:00");

        mover(admin, turno, "2026-11-17", "12:15")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("HORARIO_OCUPADO"));

        assertThat(jdbc.queryForObject("SELECT inicio = '2026-11-17T10:00-03:00'::timestamptz FROM turno WHERE id = ?",
                Boolean.class, turno)).isTrue();
    }

    @Test
    @DisplayName("HU-29: mover una reserva web mantiene su link, que vence 30 días después del horario nuevo")
    void moverUnaReservaMantieneElLink() throws Exception {
        UUID turno = turno(anaId, null, "10:00", "10:30");
        jdbc.update("UPDATE turno SET tipo = 'reserva', token_hash = 'abc', token_vence_en = '2026-12-17T10:30-03:00' WHERE id = ?", turno);

        mover(admin, turno, "2026-11-20", "10:00").andExpect(status().isOk());

        assertThat(jdbc.queryForObject("SELECT token_hash FROM turno WHERE id = ?", String.class, turno)).isEqualTo("abc");
        assertThat(jdbc.queryForObject("SELECT token_vence_en = '2026-12-20T10:30-03:00'::timestamptz FROM turno WHERE id = ?",
                Boolean.class, turno)).isTrue();
    }

    @Test
    @DisplayName("HU-29 CA3: desde el panel se cancela aunque falten menos de 3 horas; cancelarlo otra vez no cambia nada")
    void cancelarSinPlazo() throws Exception {
        UUID turno = turno(anaId, null, "10:00", "10:30");
        reloj.fijar(Instant.parse("2026-11-17T12:30:00Z"));   // martes 9:30: falta media hora

        cancelar(admin, turno).andExpect(status().isOk());
        cancelar(admin, turno).andExpect(status().isOk());

        assertThat(jdbc.queryForObject("SELECT estado FROM turno WHERE id = ?", String.class, turno)).isEqualTo("cancelado");
    }

    @Test
    @DisplayName("HU-29: un profesional no mueve ni cancela turnos de otro (404)")
    void profesionalNoTocaLosDeOtro() throws Exception {
        UUID deBruno = turno(brunoId, null, "10:00", "10:30");

        mover(ana, deBruno, "2026-11-17", "11:00").andExpect(status().isNotFound());
        cancelar(ana, deBruno).andExpect(status().isNotFound());

        assertThat(jdbc.queryForObject("SELECT estado FROM turno WHERE id = ?", String.class, deBruno)).isEqualTo("confirmado");
    }

    private ResultActions manual(Cookie sesion, String profesional, String fecha, String hora, String nombre, String telefono)
            throws Exception {
        String cuerpo = "{" + (profesional == null ? "" : "\"profesional\": \"" + profesional + "\", ")
                + "\"fecha\": \"" + fecha + "\", \"hora\": \"" + hora + "\", \"servicioIds\": [\"" + corteId + "\"], "
                + "\"clienteNombre\": \"" + nombre + "\"" + (telefono == null ? "" : ", \"clienteTelefono\": \"" + telefono + "\"") + "}";
        return mockMvc.perform(post("/api/panel/turnos").header(HttpHeaders.HOST, "demo.localhost").cookie(sesion)
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo).with(PanelDeTest.conCsrf()));
    }

    private ResultActions mover(Cookie sesion, UUID turno, String fecha, String hora) throws Exception {
        return mockMvc.perform(post("/api/panel/turnos/" + turno + "/mover").header(HttpHeaders.HOST, "demo.localhost")
                .cookie(sesion).contentType(MediaType.APPLICATION_JSON)
                .content("{\"fecha\": \"" + fecha + "\", \"hora\": \"" + hora + "\"}").with(PanelDeTest.conCsrf()));
    }

    private ResultActions cancelar(Cookie sesion, UUID turno) throws Exception {
        return mockMvc.perform(post("/api/panel/turnos/" + turno + "/cancelar").header(HttpHeaders.HOST, "demo.localhost")
                .cookie(sesion).with(PanelDeTest.conCsrf()));
    }

    private ResultActions horas(UUID profesional) throws Exception {
        return mockMvc.perform(get("/api/publico/disponibilidad/horas").header(HttpHeaders.HOST, "demo.localhost")
                .param("servicios", corteId.toString()).param("profesional", profesional.toString()).param("fecha", "2026-11-17"));
    }

    private static String idDe(ResultActions resultado) throws Exception {
        return JsonPath.read(resultado.andReturn().getResponse().getContentAsString(), "$.id");
    }

    private UUID profesional(String nombre, int orden) {
        UUID id = jdbc.queryForObject("INSERT INTO profesional (local_id, nombre, orden) VALUES (?, ?, ?) RETURNING id",
                UUID.class, demoId, nombre, orden);
        jdbc.update("INSERT INTO profesional_servicio (local_id, profesional_id, servicio_id) VALUES (?, ?, ?)", demoId, id, corteId);
        jdbc.update("INSERT INTO horario (local_id, profesional_id, dia_semana, hora_inicio, hora_fin) VALUES (?, ?, 2, '10:00', '20:00')",
                demoId, id);
        return id;
    }

    private UUID turno(UUID profesional, UUID cliente, String desde, String hasta) {
        UUID elCliente = cliente != null ? cliente
                : jdbc.queryForObject("INSERT INTO cliente (local_id, nombre) VALUES (?, 'Lucía') RETURNING id", UUID.class, demoId);
        return jdbc.queryForObject("""
                INSERT INTO turno (local_id, profesional_id, cliente_id, tipo, estado, inicio, fin, ocupado_hasta)
                VALUES (?, ?, ?, 'manual', 'confirmado', ?::timestamptz, ?::timestamptz, ?::timestamptz) RETURNING id
                """, UUID.class, demoId, profesional, elCliente, "2026-11-17T" + desde + "-03:00", "2026-11-17T" + hasta + "-03:00",
                "2026-11-17T" + hasta + "-03:00");
    }

    private void bloqueo(UUID profesional, String desde, String hasta) {
        jdbc.update("""
                INSERT INTO turno (local_id, profesional_id, tipo, estado, inicio, fin, ocupado_hasta)
                VALUES (?, ?, 'bloqueo', 'confirmado', ?::timestamptz, ?::timestamptz, ?::timestamptz)
                """, demoId, profesional, "2026-11-17T" + desde + "-03:00", "2026-11-17T" + hasta + "-03:00",
                "2026-11-17T" + hasta + "-03:00");
    }
}
