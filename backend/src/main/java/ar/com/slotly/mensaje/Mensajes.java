package ar.com.slotly.mensaje;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import ar.com.slotly.SlotlyProperties;
import ar.com.slotly.local.Local;

// Encola los mensajes al cliente (HU-35, HU-36). Corre dentro de la transacción de quien lo llama: si la reserva se
// deshace, los mensajes también. Los manda TrabajoDeMensajes, a su hora.
@Component
public class Mensajes {

    // HU-36: el recordatorio sale un día antes.
    static final Duration ANTES_DEL_TURNO = Duration.ofHours(24);

    private final JdbcTemplate jdbc;
    private final Clock reloj;
    private final String urlSitio;

    Mensajes(JdbcTemplate jdbc, Clock reloj, SlotlyProperties propiedades) {
        this.jdbc = jdbc;
        this.reloj = reloj;
        this.urlSitio = propiedades.urlSitio();
    }

    // Un turno reservado desde la web (o el nuevo de una reprogramación): la confirmación sale ya, con el link
    // personal (HU-35 CA1), y el recordatorio 24 h antes, salvo que falte menos que eso (HU-36 CA2). El token llega
    // en claro solo acá: el link se guarda en el mensaje hasta que sale (ver V11).
    public void alReservar(Local local, UUID turnoId, String token, Instant inicio) {
        Instant ahora = reloj.instant();
        String link = urlSitio.replace("{subdominio}", local.getSubdominio()) + "/turno/" + turnoId + "#t=" + token;
        encolar(local, turnoId, "confirmacion", link, ahora, ahora);
        Instant recordatorio = inicio.minus(ANTES_DEL_TURNO);
        if (recordatorio.isAfter(ahora)) {
            encolar(local, turnoId, "recordatorio", link, recordatorio, ahora);
        }
    }

    // HU-29 + HU-36: el panel movió el turno (misma fila, mismo link). El recordatorio pendiente sigue al turno; si
    // con el horario nuevo ya no llega a salir 24 h antes, se descarta, como si se hubiera reservado con poco tiempo.
    public void alMover(Local local, UUID turnoId, Instant inicioNuevo) {
        Instant ahora = reloj.instant();
        Instant recordatorio = inicioNuevo.minus(ANTES_DEL_TURNO);
        if (recordatorio.isAfter(ahora)) {
            jdbc.update("""
                    UPDATE mensaje SET enviar_en = ?
                    WHERE local_id = ? AND turno_id = ? AND tipo = 'recordatorio' AND estado = 'pendiente'
                    """, Timestamp.from(recordatorio), local.getId(), turnoId);
        } else {
            jdbc.update("""
                    UPDATE mensaje SET estado = 'descartado', link = NULL, terminado_en = ?
                    WHERE local_id = ? AND turno_id = ? AND tipo = 'recordatorio' AND estado = 'pendiente'
                    """, Timestamp.from(ahora), local.getId(), turnoId);
        }
    }

    // Si ya estaba encolado, no hace nada: un mensaje de cada tipo por turno (la clave única de V11).
    private void encolar(Local local, UUID turnoId, String tipo, String link, Instant enviarEn, Instant ahora) {
        jdbc.update("""
                INSERT INTO mensaje (local_id, turno_id, tipo, link, enviar_en, creado_en) VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (turno_id, tipo) DO NOTHING
                """, local.getId(), turnoId, tipo, link, Timestamp.from(enviarEn), Timestamp.from(ahora));
    }
}
