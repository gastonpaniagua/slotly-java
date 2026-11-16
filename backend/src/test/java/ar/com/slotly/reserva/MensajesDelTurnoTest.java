package ar.com.slotly.reserva;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.jayway.jsonpath.JsonPath;

import ar.com.slotly.BaseDeTest;
import ar.com.slotly.IntegrationTest;
import ar.com.slotly.MensajesEnviadosDeTest;
import ar.com.slotly.MensajesEnviadosDeTest.Enviado;
import ar.com.slotly.RelojDeTest;
import ar.com.slotly.local.Local;
import ar.com.slotly.local.LocalResolver;
import ar.com.slotly.mensaje.TrabajoDeMensajes;
import ar.com.slotly.panel.PanelDeTest;
import ar.com.slotly.reserva.ApartadoService.Apartado;
import ar.com.slotly.reserva.ConfirmacionService.DatosDelCliente;
import ar.com.slotly.seguridad.IntentosDeIngreso;
import ar.com.slotly.seguridad.LimiteDePedidos;

// HU-35 (confirmación con el link) y HU-36 (recordatorio 24 h antes): qué se encola al reservar, reprogramar y mover,
// y qué termina saliendo, contra la app completa.
// Reloj: lunes 16/11/2026 a las 9:00 en Buenos Aires. Lucía reserva el martes 17 a las 10:00 (13:00 UTC) con Ana.
@IntegrationTest
class MensajesDelTurnoTest {

    private static final LocalDate MARTES = LocalDate.of(2026, 11, 17);
    private static final Instant LUNES_9 = Instant.parse("2026-11-16T12:00:00Z");
    private static final Instant LUNES_10 = Instant.parse("2026-11-16T13:00:00Z");
    private static final String TELEFONO = "+5491123456789";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    RelojDeTest reloj;

    @Autowired
    LimiteDePedidos limite;

    @Autowired
    IntentosDeIngreso intentos;

    @Autowired
    PasswordEncoder encoder;

    @Autowired
    ApartadoService apartados;

    @Autowired
    ConfirmacionService confirmaciones;

    @Autowired
    LocalResolver localResolver;

    @Autowired
    TrabajoDeMensajes trabajo;

    @Autowired
    MensajesEnviadosDeTest enviados;

    UUID demoId;
    UUID corteId;
    UUID anaId;
    Apartado turno;

    @BeforeEach
    void preparar() {
        reloj.fijar(LUNES_9);
        limite.reiniciar();
        intentos.reiniciar();
        enviados.reiniciar();
        BaseDeTest.vaciar(jdbc);
        demoId = jdbc.queryForObject("INSERT INTO local (nombre, subdominio) VALUES ('Barbería Demo', 'demo') RETURNING id", UUID.class);
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
    @DisplayName("HU-35 CA1: al reservar sale la confirmación con el local, el día en palabras, la hora del local y el link")
    void confirmacion() {
        trabajo.enviarPendientes();

        assertThat(enviados.enviados()).containsExactly(new Enviado(TELEFONO,
                "Hola, Lucía. Tu turno en Barbería Demo quedó reservado para el martes 17 de noviembre a las 10:00. "
                        + "Para verlo, cancelarlo o cambiarlo: " + link(turno.id(), turno.token())));
    }

    @Test
    @DisplayName("HU-35 CA2: confirmar dos veces y correr el trabajo dos veces deja una sola confirmación enviada")
    void unaSolaConfirmacion() {
        confirmaciones.confirmar(demo(), turno.id(), turno.token(),
                new DatosDelCliente("Lucía", "11 2345-6789", null, "efectivo"));

        trabajo.enviarPendientes();
        trabajo.enviarPendientes();

        assertThat(cantidad("confirmacion")).isEqualTo(1);
        assertThat(enviados.enviados()).hasSize(1);
    }

    @Test
    @DisplayName("HU-36 CA1: el recordatorio sale 24 h antes, con día, hora y link; un minuto antes, todavía no")
    void recordatorio() {
        trabajo.enviarPendientes();
        reloj.fijar(LUNES_10.minusSeconds(60));
        trabajo.enviarPendientes();
        assertThat(enviados.enviados()).hasSize(1);

        reloj.fijar(LUNES_10);
        trabajo.enviarPendientes();

        assertThat(enviados.enviados()).hasSize(2);
        assertThat(enviados.enviados().get(1)).isEqualTo(new Enviado(TELEFONO,
                "Hola, Lucía. Te recordamos tu turno en Barbería Demo el martes 17 de noviembre a las 10:00. "
                        + "Si no podés venir, cancelalo o cambialo desde acá: " + link(turno.id(), turno.token())));
    }

    @Test
    @DisplayName("HU-36 CA2: un turno reservado con menos de 24 h de anticipación no tiene recordatorio")
    void conPocaAnticipacion() {
        reloj.fijar(Instant.parse("2026-11-16T15:00:00Z"));   // lunes 12:00: el martes a las 11:00 está a 23 h

        Apartado cerca = reservar("11:00");

        assertThat(jdbc.queryForObject("SELECT count(*) FROM mensaje WHERE turno_id = ? AND tipo = 'recordatorio'",
                Integer.class, cerca.id())).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM mensaje WHERE turno_id = ? AND tipo = 'confirmacion'",
                Integer.class, cerca.id())).isEqualTo(1);
    }

    @Test
    @DisplayName("HU-36 CA3: un turno cancelado antes del recordatorio no lo recibe")
    void cancelado() throws Exception {
        mockMvc.perform(post("/api/publico/turnos/" + turno.id() + "/cancelar")
                        .header(HttpHeaders.HOST, "demo.localhost")
                        .header(ConfirmacionController.HEADER_TOKEN, turno.token()))
                .andExpect(status().isOk());

        reloj.fijar(LUNES_10);
        trabajo.enviarPendientes();

        assertThat(estadoDe(turno.id(), "recordatorio")).isEqualTo("descartado");
        assertThat(enviados.enviados()).extracting(Enviado::texto).noneMatch(texto -> texto.contains("Te recordamos"));
    }

    @Test
    @DisplayName("HU-36 CA3: al reprogramar, el recordatorio del turno viejo no sale y el nuevo tiene el suyo (y su confirmación)")
    void reprogramado() throws Exception {
        MvcResult resultado = mockMvc.perform(post("/api/publico/turnos/" + turno.id() + "/reprogramar")
                        .header(HttpHeaders.HOST, "demo.localhost")
                        .header(ConfirmacionController.HEADER_TOKEN, turno.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fecha\": \"2026-11-17\", \"hora\": \"11:00\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String json = resultado.getResponse().getContentAsString();
        UUID nuevo = UUID.fromString(JsonPath.read(json, "$.id"));
        String tokenNuevo = JsonPath.read(json, "$.token");

        trabajo.enviarPendientes();
        assertThat(enviados.enviados()).extracting(Enviado::texto).contains(
                "Hola, Lucía. Tu turno en Barbería Demo quedó reservado para el martes 17 de noviembre a las 11:00. "
                        + "Para verlo, cancelarlo o cambiarlo: " + link(nuevo, tokenNuevo));
        reloj.fijar(LUNES_10);
        trabajo.enviarPendientes();
        assertThat(estadoDe(turno.id(), "recordatorio")).isEqualTo("descartado");
        assertThat(estadoDe(nuevo, "recordatorio")).isEqualTo("pendiente");

        reloj.fijar(LUNES_10.plusSeconds(3600));
        trabajo.enviarPendientes();
        assertThat(estadoDe(nuevo, "recordatorio")).isEqualTo("enviado");
        assertThat(enviados.enviados()).extracting(Enviado::texto).filteredOn(texto -> texto.contains("Te recordamos"))
                .containsExactly("Hola, Lucía. Te recordamos tu turno en Barbería Demo el martes 17 de noviembre a las 11:00. "
                        + "Si no podés venir, cancelalo o cambialo desde acá: " + link(nuevo, tokenNuevo));
    }

    @Test
    @DisplayName("HU-29 + HU-36: si el panel mueve el turno, el recordatorio se corre con él; si ya no llega a las 24 h, se descarta")
    void movidoDesdeElPanel() throws Exception {
        jdbc.update("INSERT INTO usuario (local_id, email, password_hash, rol) VALUES (?, 'duenio@demo.com', ?, 'admin')",
                demoId, encoder.encode("clave"));
        var admin = PanelDeTest.ingresar(mockMvc, "demo", "duenio@demo.com", "clave");

        mover(admin, "2026-11-17", "12:00");
        assertThat(enviarEn(turno.id(), "recordatorio")).isEqualTo(Instant.parse("2026-11-16T15:00:00Z"));

        mover(admin, "2026-11-16", "18:00");   // hoy a las 18: faltan menos de 24 h
        assertThat(estadoDe(turno.id(), "recordatorio")).isEqualTo("descartado");
    }

    private void mover(jakarta.servlet.http.Cookie admin, String fecha, String hora) throws Exception {
        mockMvc.perform(post("/api/panel/turnos/" + turno.id() + "/mover")
                        .header(HttpHeaders.HOST, "demo.localhost").cookie(admin).with(PanelDeTest.conCsrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fecha\": \"" + fecha + "\", \"hora\": \"" + hora + "\"}"))
                .andExpect(status().isOk());
    }

    private Apartado reservar(String hora) {
        Apartado apartado = apartados.apartar(demo(), new ApartadoService.Pedido(Set.of(corteId), anaId.toString(),
                MARTES, LocalTime.parse(hora), null));
        confirmaciones.confirmar(demo(), apartado.id(), apartado.token(),
                new DatosDelCliente("Lucía", "11 2345-6789", null, "efectivo"));
        return apartado;
    }

    private Local demo() {
        return localResolver.resolver("demo.localhost");
    }

    private static String link(UUID id, String token) {
        return "http://demo.localhost:5173/turno/" + id + "#t=" + token;
    }

    private int cantidad(String tipo) {
        return jdbc.queryForObject("SELECT count(*) FROM mensaje WHERE tipo = ?", Integer.class, tipo);
    }

    private String estadoDe(UUID turnoId, String tipo) {
        return jdbc.queryForObject("SELECT estado FROM mensaje WHERE turno_id = ? AND tipo = ?", String.class, turnoId, tipo);
    }

    private Instant enviarEn(UUID turnoId, String tipo) {
        return jdbc.queryForObject("SELECT enviar_en FROM mensaje WHERE turno_id = ? AND tipo = ?", Timestamp.class,
                turnoId, tipo).toInstant();
    }
}
