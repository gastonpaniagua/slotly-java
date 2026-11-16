package ar.com.slotly.notificacion;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import ar.com.slotly.local.Local;

// Crea los avisos al profesional (HU-37). Corre dentro de la transacción de quien lo llama: si el cambio se deshace,
// el aviso también. El aviso es para el profesional del turno.
@Component
public class Notificaciones {

    private final JdbcTemplate jdbc;
    private final Clock reloj;

    Notificaciones(JdbcTemplate jdbc, Clock reloj) {
        this.jdbc = jdbc;
        this.reloj = reloj;
    }

    // Lo que hizo el cliente desde la web (CA1): siempre avisa.
    public void delCliente(Local local, UUID turnoId, String tipo, Instant inicioAnterior) {
        avisar(local, turnoId, tipo, inicioAnterior, null);
    }

    // Lo que se hizo desde el panel: avisa salvo que lo haya hecho el mismo profesional del turno (CA3).
    // quien: el profesional del usuario que lo hizo (null si es un admin sin agenda propia).
    public void delPanel(Local local, UUID turnoId, String tipo, Instant inicioAnterior, UUID quien) {
        avisar(local, turnoId, tipo, inicioAnterior, quien);
    }

    private void avisar(Local local, UUID turnoId, String tipo, Instant inicioAnterior, UUID quien) {
        jdbc.update("""
                INSERT INTO notificacion (local_id, profesional_id, turno_id, tipo, inicio_anterior, creada_en)
                SELECT local_id, profesional_id, id, ?, ?, ? FROM turno
                WHERE id = ? AND local_id = ? AND profesional_id IS DISTINCT FROM ?::uuid
                """, tipo, inicioAnterior == null ? null : Timestamp.from(inicioAnterior), Timestamp.from(reloj.instant()),
                turnoId, local.getId(), quien);
    }
}
