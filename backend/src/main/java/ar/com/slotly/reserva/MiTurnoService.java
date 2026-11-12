package ar.com.slotly.reserva;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ar.com.slotly.error.SlotlyException;
import ar.com.slotly.local.Local;
import ar.com.slotly.reserva.ConfirmacionService.TurnoPublico;

// Lo que el cliente puede hacer con un turno ya confirmado desde su link: cancelarlo (HU-20) y, en el paso 43,
// reprogramarlo (HU-21). Las dos acciones tienen el mismo plazo: hasta cancelar_hasta_horas antes del turno.
@Service
class MiTurnoService {

    private final JdbcTemplate jdbc;
    private final Clock reloj;
    private final ConfirmacionService confirmaciones;

    MiTurnoService(JdbcTemplate jdbc, Clock reloj, ConfirmacionService confirmaciones) {
        this.jdbc = jdbc;
        this.reloj = reloj;
        this.confirmaciones = confirmaciones;
    }

    // HU-20: cancela el turno. El horario vuelve a ofrecerse solo, porque un turno cancelado no ocupa (doc 03).
    @Transactional
    TurnoPublico cancelar(Local local, UUID id, String token) {
        Map<String, Object> turno = bloquear(local, id, token);
        // CA3: cancelarlo otra vez no cambia nada; se responde el turno como está.
        if ("cancelado".equals(turno.get("estado"))) {
            return confirmaciones.ver(local, id);
        }
        exigirQueSePuedaCambiar(local, turno);
        jdbc.update("UPDATE turno SET estado = 'cancelado' WHERE id = ?", id);
        return confirmaciones.ver(local, id);
    }

    // Bloquea la fila del turno hasta el final de la transacción: si llegan dos pedidos a la vez (dos pestañas),
    // el segundo espera y ve el resultado del primero. Mismas respuestas que "ver" (HU-19): 404 si el token no
    // corresponde o el turno es de otro local, 410 si el link venció.
    private Map<String, Object> bloquear(Local local, UUID id, String token) {
        Map<String, Object> turno;
        try {
            turno = jdbc.queryForMap("""
                    SELECT estado, inicio, token_vence_en FROM turno
                    WHERE id = ? AND local_id = ? AND token_hash = ?
                    FOR UPDATE
                    """, id, local.getId(), TokenSecreto.hash(token));
        } catch (EmptyResultDataAccessException e) {
            throw ConfirmacionService.turnoNoEncontrado();
        }
        Timestamp venceEn = (Timestamp) turno.get("token_vence_en");
        if (venceEn == null || !venceEn.toInstant().isAfter(reloj.instant())) {
            throw new SlotlyException(HttpStatus.GONE, "LINK_VENCIDO", "Este link venció.");
        }
        return turno;
    }

    // HU-20 CA2: solo un turno confirmado y con más de cancelar_hasta_horas por delante. Si no, decide el local.
    private void exigirQueSePuedaCambiar(Local local, Map<String, Object> turno) {
        Instant inicio = ((Timestamp) turno.get("inicio")).toInstant();
        Instant limite = inicio.minus(Duration.ofHours(local.getCancelarHastaHoras()));
        if (!"confirmado".equals(turno.get("estado")) || !reloj.instant().isBefore(limite)) {
            throw new SlotlyException(HttpStatus.CONFLICT, "FUERA_DE_PLAZO",
                    "Este turno ya no se puede cambiar desde acá. Escribile al local.");
        }
    }
}
