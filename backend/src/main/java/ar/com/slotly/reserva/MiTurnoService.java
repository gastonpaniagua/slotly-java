package ar.com.slotly.reserva;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ar.com.slotly.disponibilidad.DisponibilidadService;
import ar.com.slotly.disponibilidad.DisponibilidadService.DiasConLugar;
import ar.com.slotly.disponibilidad.DisponibilidadService.HorarioReservable;
import ar.com.slotly.error.SlotlyException;
import ar.com.slotly.local.Local;
import ar.com.slotly.reserva.ConfirmacionService.TurnoPublico;

// Lo que el cliente puede hacer con un turno ya confirmado desde su link: cancelarlo (HU-20) y reprogramarlo
// (HU-21). Las dos acciones tienen el mismo plazo: hasta cancelar_hasta_horas antes del turno.
@Service
class MiTurnoService {

    private final JdbcTemplate jdbc;
    private final Clock reloj;
    private final ConfirmacionService confirmaciones;
    private final DisponibilidadService disponibilidad;
    private final CodigoService codigos;

    MiTurnoService(JdbcTemplate jdbc, Clock reloj, ConfirmacionService confirmaciones,
            DisponibilidadService disponibilidad, CodigoService codigos) {
        this.jdbc = jdbc;
        this.reloj = reloj;
        this.confirmaciones = confirmaciones;
        this.disponibilidad = disponibilidad;
        this.codigos = codigos;
    }

    // "Mi turno" (HU-19): el turno, si la credencial es suya.
    @Transactional(readOnly = true)
    TurnoPublico ver(Local local, UUID id, Credencial credencial) {
        leer(local, id, credencial);
        return confirmaciones.ver(local, id);
    }

    // El turno nuevo de una reprogramación y el token de su link (el viejo queda cancelado).
    record TurnoReprogramado(UUID id, String token, TurnoPublico turno) {
    }

    // Lo que no cambia al reprogramar: el profesional y los servicios del turno (HU-21 CA1).
    private record QueReprogramar(UUID profesionalId, Set<UUID> servicios) {
    }

    // HU-20: cancela el turno. El horario vuelve a ofrecerse solo, porque un turno cancelado no ocupa (doc 03).
    @Transactional
    TurnoPublico cancelar(Local local, UUID id, Credencial credencial) {
        Map<String, Object> turno = bloquear(local, id, credencial);
        // CA3: cancelarlo otra vez no cambia nada; se responde el turno como está.
        if ("cancelado".equals(turno.get("estado"))) {
            return confirmaciones.ver(local, id);
        }
        exigirQueSePuedaCambiar(local, turno);
        jdbc.update("UPDATE turno SET estado = 'cancelado' WHERE id = ?", id);
        return confirmaciones.ver(local, id);
    }

    // HU-21 CA1: los días con lugar para mover el turno, con sus mismos servicios y profesional, sin que el
    // horario actual cuente como ocupado.
    @Transactional(readOnly = true)
    DiasConLugar diasParaReprogramar(Local local, UUID id, Credencial credencial, LocalDate desde, LocalDate hasta) {
        QueReprogramar que = queReprogramar(local, id, leer(local, id, credencial));
        return disponibilidad.dias(local, que.servicios(), que.profesionalId().toString(), desde, hasta, id);
    }

    @Transactional(readOnly = true)
    List<LocalTime> horasParaReprogramar(Local local, UUID id, Credencial credencial, LocalDate fecha) {
        QueReprogramar que = queReprogramar(local, id, leer(local, id, credencial));
        return disponibilidad.horas(local, que.servicios(), que.profesionalId().toString(), fecha, id);
    }

    // HU-21 CA2: en una sola transacción, el turno viejo queda cancelado y el nuevo, confirmado. Si el horario nuevo
    // se ocupó (CA3), la transacción se deshace entera y el viejo sigue intacto.
    @Transactional
    TurnoReprogramado reprogramar(Local local, UUID id, Credencial credencial, LocalDate fecha, LocalTime hora) {
        Map<String, Object> viejo = bloquear(local, id, credencial);
        QueReprogramar que = queReprogramar(local, id, viejo);

        // El candado por profesional de los apartados (ver ApartadoService): los que reservan a la vez entran de a uno.
        jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?::text, 0))", que.profesionalId());
        // Primero se cancela el viejo: así el nuevo puede pisarse con él (por ejemplo, correrlo media hora).
        jdbc.update("UPDATE turno SET estado = 'cancelado' WHERE id = ?", id);

        HorarioReservable horario = disponibilidad
                .reservable(local, que.servicios(), que.profesionalId().toString(), fecha, hora)
                .orElseThrow(MiTurnoService::horarioOcupado);
        Instant ahora = reloj.instant();
        // Los apartados vencidos que siguen como 'apartado' todavía bloquean en el constraint (doc 03).
        jdbc.update("""
                UPDATE turno SET estado = 'vencido'
                WHERE local_id = ? AND profesional_id = ? AND estado = 'apartado' AND vence_en <= ?
                  AND inicio < ? AND ocupado_hasta > ?
                """, local.getId(), que.profesionalId(), Timestamp.from(ahora),
                Timestamp.from(horario.ocupadoHasta()), Timestamp.from(horario.inicio()));

        String tokenNuevo = TokenSecreto.nuevo();
        UUID nuevo;
        try {
            nuevo = jdbc.queryForObject("""
                    INSERT INTO turno (local_id, profesional_id, cliente_id, tipo, estado, inicio, fin, ocupado_hasta,
                                       token_hash, token_vence_en, forma_pago, nota, reprogramado_de_id)
                    VALUES (?, ?, ?, 'reserva', 'confirmado', ?, ?, ?, ?, ?, ?, ?, ?)
                    RETURNING id
                    """, UUID.class, local.getId(), que.profesionalId(), viejo.get("cliente_id"),
                    Timestamp.from(horario.inicio()), Timestamp.from(horario.fin()),
                    Timestamp.from(horario.ocupadoHasta()), TokenSecreto.hash(tokenNuevo),
                    Timestamp.from(horario.fin().plus(ConfirmacionService.TOKEN_DESPUES_DEL_TURNO)),
                    viejo.get("forma_pago"), viejo.get("nota"), id);
        } catch (DataIntegrityViolationException e) {
            if (String.valueOf(e.getMessage()).contains("turno_sin_superposicion")) {
                throw horarioOcupado();
            }
            throw e;
        } catch (PessimisticLockingFailureException e) {
            throw horarioOcupado();
        }

        // HU-21 CA5: los precios (y duraciones) copiados son los del turno original, no los de hoy.
        jdbc.update("""
                INSERT INTO turno_servicio (local_id, turno_id, servicio_id, duracion_min, precio)
                SELECT local_id, ?, servicio_id, duracion_min, precio FROM turno_servicio WHERE turno_id = ?
                """, nuevo, id);
        return new TurnoReprogramado(nuevo, tokenNuevo, confirmaciones.ver(local, nuevo));
    }

    // Qué reprogramar, si se puede: el turno tiene que poder cambiarse (HU-21 CA4, mismo plazo que cancelar).
    private QueReprogramar queReprogramar(Local local, UUID id, Map<String, Object> turno) {
        exigirQueSePuedaCambiar(local, turno);
        Set<UUID> servicios = new HashSet<>(jdbc.queryForList(
                "SELECT servicio_id FROM turno_servicio WHERE turno_id = ? AND local_id = ?", UUID.class, id, local.getId()));
        return new QueReprogramar((UUID) turno.get("profesional_id"), servicios);
    }

    private static SlotlyException horarioOcupado() {
        return new SlotlyException(HttpStatus.CONFLICT, "HORARIO_OCUPADO", "Ese horario se acaba de ocupar, elegí otro.");
    }

    // Lee el turno sin bloquearlo (para mostrar días y horas). Mismas respuestas que bloquear.
    private Map<String, Object> leer(Local local, UUID id, Credencial credencial) {
        return buscar(local, id, credencial, "");
    }

    // Bloquea la fila del turno hasta el final de la transacción: si llegan dos pedidos a la vez (dos pestañas),
    // el segundo espera y ve el resultado del primero. Mismas respuestas que "ver" (HU-19): 404 si el token no
    // corresponde o el turno es de otro local, 410 si el link venció.
    private Map<String, Object> bloquear(Local local, UUID id, Credencial credencial) {
        return buscar(local, id, credencial, "FOR UPDATE");
    }

    // El turno, si la credencial es suya:
    // - con el acceso del cliente (HU-22), cualquier turno de ese cliente en el local, también los viejos;
    // - con el token del link, ese turno, mientras el link no venza (HU-19 CA3).
    // Sin credencial, 400; con una que no corresponde, 404 (o 401 si el acceso venció: hay que volver a entrar).
    private Map<String, Object> buscar(Local local, UUID id, Credencial credencial, String bloqueo) {
        String consulta = """
                SELECT estado, inicio, token_vence_en, profesional_id, cliente_id, forma_pago, nota FROM turno
                WHERE id = ? AND local_id = ? AND
                """;
        try {
            if (credencial.acceso() != null) {
                UUID cliente = codigos.clienteDelAcceso(local, credencial.acceso());
                return jdbc.queryForMap(consulta + " cliente_id = ? " + bloqueo, id, local.getId(), cliente);
            }
            if (credencial.token() == null) {
                throw new SlotlyException(HttpStatus.BAD_REQUEST, "VALIDACION", "Falta el link del turno.");
            }
            return validarLink(jdbc.queryForMap(consulta + " token_hash = ? " + bloqueo, id, local.getId(),
                    TokenSecreto.hash(credencial.token())));
        } catch (EmptyResultDataAccessException e) {
            throw ConfirmacionService.turnoNoEncontrado();
        }
    }

    private Map<String, Object> validarLink(Map<String, Object> turno) {
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
