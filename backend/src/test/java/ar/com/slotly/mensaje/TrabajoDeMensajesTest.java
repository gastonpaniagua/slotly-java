package ar.com.slotly.mensaje;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.sql.Connection;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import ar.com.slotly.BaseDeTest;
import ar.com.slotly.IntegrationTest;
import ar.com.slotly.MensajesEnviadosDeTest;
import ar.com.slotly.MensajesEnviadosDeTest.Enviado;
import ar.com.slotly.RelojDeTest;
import ar.com.slotly.trabajo.ApartadosVencidos;

// El trabajo que manda los mensajes (D2) y el que vence apartados, contra Postgres real.
// Reloj: lunes 16/11/2026 a las 10:00 en Buenos Aires. Lucía tiene un turno con Ana el martes 17 a las 10:00.
@IntegrationTest
class TrabajoDeMensajesTest {

    private static final Instant LUNES_10 = Instant.parse("2026-11-16T13:00:00Z");
    private static final String LINK = "http://demo.localhost:5173/turno/secreto";

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    DataSource dataSource;

    @Autowired
    RelojDeTest reloj;

    @Autowired
    MensajesEnviadosDeTest enviados;

    @Autowired
    TrabajoDeMensajes trabajo;

    @Autowired
    ApartadosVencidos apartados;

    UUID demoId;
    UUID anaId;
    UUID luciaId;
    UUID turnoId;

    @BeforeEach
    void preparar() {
        reloj.fijar(LUNES_10);
        enviados.reiniciar();
        BaseDeTest.vaciar(jdbc);
        demoId = jdbc.queryForObject("INSERT INTO local (nombre, subdominio) VALUES ('Barbería Demo', 'demo') RETURNING id", UUID.class);
        anaId = jdbc.queryForObject("INSERT INTO profesional (local_id, nombre) VALUES (?, 'Ana') RETURNING id", UUID.class, demoId);
        luciaId = jdbc.queryForObject("INSERT INTO cliente (local_id, nombre, telefono) VALUES (?, 'Lucía', '+5491123456789') RETURNING id",
                UUID.class, demoId);
        turnoId = turno("2026-11-17T13:00:00Z", "confirmado");
    }

    @Test
    @DisplayName("D2: manda un mensaje al que ya le tocaba, con el día en palabras y la hora del local, y borra el link")
    void mandaUnPendiente() {
        UUID mensaje = mensaje(turnoId, "confirmacion", LUNES_10);

        assertThat(trabajo.enviarPendientes()).isEqualTo(1);

        assertThat(enviados.enviados()).containsExactly(new Enviado("+5491123456789",
                "Hola, Lucía. Tu turno en Barbería Demo quedó reservado para el martes 17 de noviembre a las 10:00. "
                        + "Para verlo, cancelarlo o cambiarlo: " + LINK));
        assertThat(estado(mensaje)).isEqualTo("enviado");
        assertThat(jdbc.queryForObject("SELECT link IS NULL AND terminado_en IS NOT NULL FROM mensaje WHERE id = ?",
                Boolean.class, mensaje)).isTrue();
    }

    @Test
    @DisplayName("D2: un mensaje al que todavía no le toca queda pendiente")
    void todaviaNoToca() {
        UUID mensaje = mensaje(turnoId, "confirmacion", LUNES_10.plusSeconds(60));

        assertThat(trabajo.enviarPendientes()).isZero();

        assertThat(enviados.enviados()).isEmpty();
        assertThat(estado(mensaje)).isEqualTo("pendiente");
    }

    @Test
    @DisplayName("HU-35 CA2: si el trabajo corre dos veces, el mensaje sale una sola vez")
    void corriendoDosVeces() {
        mensaje(turnoId, "confirmacion", LUNES_10);

        trabajo.enviarPendientes();
        trabajo.enviarPendientes();

        assertThat(enviados.enviados()).hasSize(1);
    }

    @Test
    @DisplayName("D2: el mismo mensaje no se puede encolar dos veces para un turno (lo frena la base)")
    void unoPorTipoYTurno() {
        mensaje(turnoId, "confirmacion", LUNES_10);

        assertThatThrownBy(() -> mensaje(turnoId, "confirmacion", LUNES_10)).isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    @DisplayName("D2: un mensaje que otra instancia tiene tomado se saltea, sin esperar ni mandarlo dos veces")
    void otraInstanciaLoTieneTomado() throws Exception {
        UUID tomado = mensaje(turnoId, "confirmacion", LUNES_10);
        UUID libre = mensaje(turno("2026-11-18T13:00:00Z", "confirmado"), "confirmacion", LUNES_10);

        try (Connection otra = dataSource.getConnection()) {
            otra.setAutoCommit(false);
            try (var consulta = otra.prepareStatement("SELECT id FROM mensaje WHERE id = ? FOR UPDATE")) {
                consulta.setObject(1, tomado);
                consulta.executeQuery();
            }

            // Tope, no espera: con SKIP LOCKED vuelve enseguida; sin él se quedaría esperando el candado para siempre.
            assertThat(assertTimeoutPreemptively(Duration.ofSeconds(10), trabajo::enviarPendientes)).isEqualTo(1);
            otra.rollback();
        }

        assertThat(estado(libre)).isEqualTo("enviado");
        assertThat(estado(tomado)).isEqualTo("pendiente");
        assertThat(enviados.enviados()).hasSize(1);
    }

    @Test
    @DisplayName("HU-35 CA3: si el envío falla se reintenta a 1, 5 y 15 minutos, y después queda fallido sin link")
    void reintentos() {
        UUID mensaje = mensaje(turnoId, "confirmacion", LUNES_10);
        enviados.fallarLasProximas(4);

        trabajo.enviarPendientes();
        assertThat(enviarEn(mensaje)).isEqualTo(LUNES_10.plusSeconds(60));
        reloj.fijar(LUNES_10.plusSeconds(60));
        trabajo.enviarPendientes();
        assertThat(enviarEn(mensaje)).isEqualTo(LUNES_10.plusSeconds(60 + 5 * 60));
        reloj.fijar(LUNES_10.plusSeconds(60 + 5 * 60));
        trabajo.enviarPendientes();
        assertThat(enviarEn(mensaje)).isEqualTo(LUNES_10.plusSeconds(60 + 5 * 60 + 15 * 60));
        assertThat(estado(mensaje)).isEqualTo("pendiente");
        reloj.fijar(LUNES_10.plusSeconds(60 + 5 * 60 + 15 * 60));
        trabajo.enviarPendientes();

        assertThat(estado(mensaje)).isEqualTo("fallido");
        assertThat(jdbc.queryForObject("SELECT intentos FROM mensaje WHERE id = ?", Integer.class, mensaje)).isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT ultimo_error FROM mensaje WHERE id = ?", String.class, mensaje))
                .isEqualTo("El proveedor no respondió");
        assertThat(jdbc.queryForObject("SELECT link IS NULL FROM mensaje WHERE id = ?", Boolean.class, mensaje)).isTrue();
        assertThat(enviados.enviados()).isEmpty();
    }

    @Test
    @DisplayName("HU-35 CA3: si un reintento sale bien, queda enviado")
    void reintentoQueSaleBien() {
        UUID mensaje = mensaje(turnoId, "confirmacion", LUNES_10);
        enviados.fallarLasProximas(1);

        trabajo.enviarPendientes();
        reloj.fijar(LUNES_10.plusSeconds(60));
        trabajo.enviarPendientes();

        assertThat(estado(mensaje)).isEqualTo("enviado");
        assertThat(enviados.enviados()).hasSize(1);
    }

    @Test
    @DisplayName("D2: si a su hora ya no corresponde (turno cancelado, cliente sin teléfono), se descarta sin mandarlo")
    void seDescarta() {
        // Lucía tiene teléfono: su recordatorio se descarta solo porque el turno se canceló.
        UUID cancelado = mensaje(turno("2026-11-18T13:00:00Z", "cancelado"), "recordatorio", LUNES_10);
        UUID pedroId = jdbc.queryForObject("INSERT INTO cliente (local_id, nombre) VALUES (?, 'Pedro') RETURNING id",
                UUID.class, demoId);
        jdbc.update("UPDATE turno SET cliente_id = ? WHERE id = ?", pedroId, turnoId);
        UUID sinTelefono = mensaje(turnoId, "confirmacion", LUNES_10);

        assertThat(trabajo.enviarPendientes()).isEqualTo(2);

        assertThat(estado(cancelado)).isEqualTo("descartado");
        assertThat(estado(sinTelefono)).isEqualTo("descartado");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM mensaje WHERE link IS NOT NULL", Integer.class)).isZero();
        assertThat(enviados.enviados()).isEmpty();
    }

    @Test
    @DisplayName("Apartados: el trabajo pasa a 'vencido' los apartados cuyo tiempo se cumplió, y solo esos")
    void apartadosVencidos() {
        UUID vencido = apartado("2026-11-18T14:00:00Z", LUNES_10.minusSeconds(1));
        UUID vigente = apartado("2026-11-18T15:00:00Z", LUNES_10.plusSeconds(60));

        assertThat(apartados.vencer()).isEqualTo(1);

        assertThat(estadoDelTurno(vencido)).isEqualTo("vencido");
        assertThat(estadoDelTurno(vigente)).isEqualTo("apartado");
    }

    private UUID turno(String inicio, String estado) {
        Instant desde = Instant.parse(inicio);
        return jdbc.queryForObject("""
                INSERT INTO turno (local_id, profesional_id, cliente_id, tipo, estado, inicio, fin, ocupado_hasta)
                VALUES (?, ?, ?, 'reserva', ?, ?, ?, ?) RETURNING id
                """, UUID.class, demoId, anaId, luciaId, estado, Timestamp.from(desde),
                Timestamp.from(desde.plusSeconds(1800)), Timestamp.from(desde.plusSeconds(1800)));
    }

    private UUID apartado(String inicio, Instant venceEn) {
        Instant desde = Instant.parse(inicio);
        return jdbc.queryForObject("""
                INSERT INTO turno (local_id, profesional_id, tipo, estado, inicio, fin, ocupado_hasta, vence_en)
                VALUES (?, ?, 'reserva', 'apartado', ?, ?, ?, ?) RETURNING id
                """, UUID.class, demoId, anaId, Timestamp.from(desde), Timestamp.from(desde.plusSeconds(1800)),
                Timestamp.from(desde.plusSeconds(1800)), Timestamp.from(venceEn));
    }

    private UUID mensaje(UUID turno, String tipo, Instant enviarEn) {
        return jdbc.queryForObject("""
                INSERT INTO mensaje (local_id, turno_id, tipo, link, enviar_en, creado_en) VALUES (?, ?, ?, ?, ?, ?) RETURNING id
                """, UUID.class, demoId, turno, tipo, LINK, Timestamp.from(enviarEn), Timestamp.from(LUNES_10));
    }

    private String estado(UUID mensaje) {
        return jdbc.queryForObject("SELECT estado FROM mensaje WHERE id = ?", String.class, mensaje);
    }

    private String estadoDelTurno(UUID turno) {
        return jdbc.queryForObject("SELECT estado FROM turno WHERE id = ?", String.class, turno);
    }

    private Instant enviarEn(UUID mensaje) {
        return jdbc.queryForObject("SELECT enviar_en FROM mensaje WHERE id = ?", Timestamp.class, mensaje).toInstant();
    }
}
