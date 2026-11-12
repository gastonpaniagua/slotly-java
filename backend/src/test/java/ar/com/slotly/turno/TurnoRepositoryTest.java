package ar.com.slotly.turno;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import ar.com.slotly.IntegrationTest;

// Qué cuenta como "ocupado" para el cálculo de horas libres, contra Postgres real (doc 03).
@IntegrationTest
class TurnoRepositoryTest {

    private static final Instant AHORA = Instant.parse("2026-11-17T12:00:00Z");
    private static final Instant DESDE = Instant.parse("2026-11-17T12:00:00Z");
    private static final Instant HASTA = Instant.parse("2026-11-17T19:00:00Z");

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    TurnoRepository turnos;

    UUID localId;
    UUID anaId;

    @BeforeEach
    void prepararBase() {
        jdbc.execute("TRUNCATE local, servicio, profesional, cliente, turno, turno_servicio, profesional_servicio, horario");
        localId = crearLocal("demo");
        anaId = crearProfesional(localId, "Ana");
    }

    @Test
    @DisplayName("HU-07 CA3, CA4 y CA5: ocupan los turnos, bloqueos y apartados vigentes; no los cancelados, vencidos ni apartados vencidos")
    void soloCuentaLoQueOcupa() {
        insertar(anaId, "bloqueo", "confirmado", "13:00", "13:45", null);    // ocupa
        insertar(anaId, "bloqueo", "cancelado", "13:00", "13:45", null);     // cancelado: no
        insertar(anaId, "bloqueo", "vencido", "14:00", "14:30", null);       // vencido: no
        insertar(anaId, "reserva", "apartado", "15:00", "15:30", "12:05");   // apartado vigente: ocupa
        insertar(anaId, "reserva", "apartado", "16:00", "16:30", "11:55");   // apartado ya vencido: no

        assertThat(inicios(buscar(anaId))).containsExactlyInAnyOrder("13:00", "15:00");
    }

    @Test
    @DisplayName("HU-07: solo lo que se cruza con el rango pedido; el rango es semiabierto (terminar justo al empezar no cruza)")
    void soloLoQueSeCruzaConElRango() {
        // Dos profesionales, porque la base no deja que los turnos de uno mismo se pisen.
        UUID brunoId = crearProfesional(localId, "Bruno");
        insertar(anaId, "bloqueo", "confirmado", "11:00", "12:00", null);     // termina justo cuando empieza el rango
        insertar(brunoId, "bloqueo", "confirmado", "11:30", "12:30", null);   // empieza antes y termina adentro: cruza
        insertar(anaId, "bloqueo", "confirmado", "19:00", "20:00", null);     // empieza justo cuando termina el rango

        List<Turno> encontrados = turnos.buscarOcupaciones(localId, List.of(anaId, brunoId), DESDE, HASTA, AHORA);

        assertThat(inicios(encontrados)).containsExactly("11:30");
    }

    @Test
    @DisplayName("HU-04: no devuelve turnos de otro local aunque se pida por su profesional")
    void noDevuelveTurnosDeOtroLocal() {
        UUID otroLocalId = crearLocal("otro");
        UUID carlaId = crearProfesional(otroLocalId, "Carla");
        jdbc.update("""
                INSERT INTO turno (local_id, profesional_id, tipo, estado, inicio, fin, ocupado_hasta)
                VALUES (?, ?, 'bloqueo', 'confirmado', '2026-11-17T13:00:00Z', '2026-11-17T14:00:00Z', '2026-11-17T14:00:00Z')
                """, otroLocalId, carlaId);

        assertThat(turnos.buscarOcupaciones(localId, List.of(anaId, carlaId), DESDE, HASTA, AHORA)).isEmpty();
    }

    private List<Turno> buscar(UUID profesionalId) {
        return turnos.buscarOcupaciones(localId, List.of(profesionalId), DESDE, HASTA, AHORA);
    }

    private static List<String> inicios(List<Turno> lista) {
        return lista.stream().map(t -> t.getInicio().toString().substring(11, 16)).toList();
    }

    // Inserta un turno del 2026-11-17 (horas en UTC). venceEn solo para apartados.
    private void insertar(UUID profesionalId, String tipo, String estado, String inicio, String fin, String venceEn) {
        jdbc.update("""
                INSERT INTO turno (local_id, profesional_id, tipo, estado, inicio, fin, ocupado_hasta, vence_en)
                VALUES (?, ?, ?, ?, ?::timestamptz, ?::timestamptz, ?::timestamptz, ?::timestamptz)
                """, localId, profesionalId, tipo, estado,
                "2026-11-17T" + inicio + ":00Z", "2026-11-17T" + fin + ":00Z", "2026-11-17T" + fin + ":00Z",
                venceEn == null ? null : "2026-11-17T" + venceEn + ":00Z");
    }

    private UUID crearLocal(String subdominio) {
        return jdbc.queryForObject("INSERT INTO local (nombre, subdominio) VALUES (?, ?) RETURNING id",
                UUID.class, "Local " + subdominio, subdominio);
    }

    private UUID crearProfesional(UUID local, String nombre) {
        return jdbc.queryForObject("INSERT INTO profesional (local_id, nombre) VALUES (?, ?) RETURNING id",
                UUID.class, local, nombre);
    }
}
