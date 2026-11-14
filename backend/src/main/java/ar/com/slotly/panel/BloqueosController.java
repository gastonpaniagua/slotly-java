package ar.com.slotly.panel;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.annotation.JsonFormat;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import ar.com.slotly.error.SlotlyException;
import ar.com.slotly.error.ValidacionException;
import ar.com.slotly.local.Local;
import ar.com.slotly.local.LocalResolver;
import ar.com.slotly.usuario.UsuarioSesion;

// Bloqueos, feriados y vacaciones (HU-27), desde la agenda. Un bloqueo es una fila de turno con tipo 'bloqueo'
// (D6, doc 03): pasa por el mismo constraint que los turnos, así que nunca se pisa con uno (HU-13).
// El admin bloquea a cualquier profesional del local o a todos; un profesional, solo su agenda (CA3).
@RestController
class BloqueosController {

    static final String TODOS = "todos";

    private final LocalResolver localResolver;
    private final JdbcTemplate jdbc;
    private final Clock reloj;

    BloqueosController(LocalResolver localResolver, JdbcTemplate jdbc, Clock reloj) {
        this.localResolver = localResolver;
        this.jdbc = jdbc;
        this.reloj = reloj;
    }

    // desde y hasta en hora del local. Un día entero es de 00:00 a 00:00 del día siguiente.
    record BloqueoRequest(
            @Schema(types = {"string", "null"}, description = "Id del profesional o \"todos\" (solo el admin). Si falta, el del usuario")
            String profesional,
            @NotNull(message = "Elegí desde cuándo.") @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm")
            @Schema(type = "string", example = "2026-11-17T10:00") LocalDateTime desde,
            @NotNull(message = "Elegí hasta cuándo.") @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm")
            @Schema(type = "string", example = "2026-11-17T13:00") LocalDateTime hasta,
            @Schema(types = {"string", "null"})
            @Size(max = 120, message = "El motivo puede tener hasta 120 caracteres.") String motivo) {
    }

    // Un turno que impide el bloqueo (CA2): con quién y cuándo, en hora del local.
    record TurnoEnElRango(UUID id, String profesionalNombre,
            @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm") @Schema(type = "string", example = "2026-11-17T10:00") LocalDateTime inicio,
            @Schema(types = {"string", "null"}) String clienteNombre) {
    }

    record BloqueosCreados(List<UUID> ids) {
    }

    @PostMapping("/api/panel/bloqueos")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    @Operation(summary = "Bloquea un rango de un profesional o de todo el local (HU-27)")
    BloqueosCreados crear(@Parameter(hidden = true) @RequestHeader(HttpHeaders.HOST) String host,
            @AuthenticationPrincipal UsuarioSesion usuario, @Valid @RequestBody BloqueoRequest pedido) {
        Local local = localResolver.resolver(host);
        if (!pedido.hasta().isAfter(pedido.desde())) {
            throw new ValidacionException("hasta", "El bloqueo tiene que terminar después de empezar.");
        }
        List<UUID> profesionales = aQuienes(local, usuario, pedido.profesional());
        ZoneId zona = ZoneId.of(local.getZonaHoraria());
        Instant desde = pedido.desde().atZone(zona).toInstant();
        Instant hasta = pedido.hasta().atZone(zona).toInstant();

        // CA2: con turnos en el rango, no se guarda: se muestran para reprogramarlos o cancelarlos antes.
        List<TurnoEnElRango> turnos = turnosEnElRango(local, profesionales, desde, hasta, zona);
        if (!turnos.isEmpty()) {
            throw new SlotlyException(HttpStatus.CONFLICT, "TURNOS_EN_RANGO", "Hay " + turnos.size()
                    + (turnos.size() == 1 ? " turno" : " turnos") + " en ese rango.").con("turnos", turnos);
        }

        // Los apartados vencidos que siguen como 'apartado' todavía bloquean en el constraint (doc 03): se pasan a
        // 'vencido' antes de insertar, como hace la reserva.
        jdbc.update("""
                UPDATE turno SET estado = 'vencido'
                WHERE local_id = ? AND profesional_id = ANY (?) AND estado = 'apartado' AND vence_en <= ?
                  AND inicio < ? AND ocupado_hasta > ?
                """, local.getId(), profesionales.toArray(UUID[]::new), Timestamp.from(reloj.instant()),
                Timestamp.from(hasta), Timestamp.from(desde));

        String motivo = pedido.motivo() == null || pedido.motivo().isBlank() ? null : pedido.motivo().trim();
        List<UUID> ids = new ArrayList<>();
        try {
            // Un bloqueo de todo el local es una fila por profesional, en la misma transacción (doc 03).
            for (UUID profesional : profesionales) {
                ids.add(jdbc.queryForObject("""
                        INSERT INTO turno (local_id, profesional_id, tipo, estado, inicio, fin, ocupado_hasta, motivo)
                        VALUES (?, ?, 'bloqueo', 'confirmado', ?, ?, ?, ?) RETURNING id
                        """, UUID.class, local.getId(), profesional, Timestamp.from(desde), Timestamp.from(hasta),
                        Timestamp.from(hasta), motivo));
            }
        } catch (DataIntegrityViolationException | PessimisticLockingFailureException e) {
            // Otro bloqueo (o un turno que entró recién) ya ocupa parte del rango: el constraint lo frena (HU-13).
            throw new SlotlyException(HttpStatus.CONFLICT, "HORARIO_OCUPADO", "Ya hay un bloqueo o un turno en ese rango.");
        }
        return new BloqueosCreados(ids);
    }

    // CA4: al borrarlo, esas horas vuelven a ofrecerse. Un profesional solo borra los de su agenda.
    @DeleteMapping("/api/panel/bloqueos/{id}")
    @Operation(summary = "Borra un bloqueo (HU-27 CA4)")
    ResponseEntity<Void> borrar(@Parameter(hidden = true) @RequestHeader(HttpHeaders.HOST) String host,
            @AuthenticationPrincipal UsuarioSesion usuario, @PathVariable UUID id) {
        Local local = localResolver.resolver(host);
        int borrados = usuario.esAdmin()
                ? jdbc.update("DELETE FROM turno WHERE id = ? AND local_id = ? AND tipo = 'bloqueo'", id, local.getId())
                : jdbc.update("DELETE FROM turno WHERE id = ? AND local_id = ? AND tipo = 'bloqueo' AND profesional_id = ?",
                        id, local.getId(), usuario.profesionalId());
        if (borrados == 0) {
            throw new SlotlyException(HttpStatus.NOT_FOUND, "TURNO_NO_ENCONTRADO", "No existe ese bloqueo.");
        }
        return ResponseEntity.noContent().build();
    }

    // CA1 y CA3: a quién se bloquea. Un profesional, solo a sí mismo; el admin, a uno del local o a todos los activos.
    private List<UUID> aQuienes(Local local, UsuarioSesion usuario, String pedido) {
        if (!usuario.esAdmin()) {
            if (pedido != null && !pedido.equals(String.valueOf(usuario.profesionalId()))) {
                throw new SlotlyException(HttpStatus.FORBIDDEN, "SIN_PERMISO", "Solo podés bloquear tu agenda.");
            }
            return List.of(usuario.profesionalId());
        }
        if (pedido == null && usuario.profesionalId() != null) {
            return List.of(usuario.profesionalId());
        }
        if (pedido == null || pedido.equals(TODOS)) {
            return jdbc.queryForList("SELECT id FROM profesional WHERE local_id = ? AND activo ORDER BY orden, nombre",
                    UUID.class, local.getId());
        }
        try {
            UUID id = UUID.fromString(pedido);
            Integer existe = jdbc.queryForObject("SELECT count(*) FROM profesional WHERE id = ? AND local_id = ?",
                    Integer.class, id, local.getId());
            if (existe != null && existe > 0) {
                return List.of(id);
            }
        } catch (IllegalArgumentException e) {
            // cae al 404 de abajo
        }
        throw new SlotlyException(HttpStatus.NOT_FOUND, "PROFESIONAL_NO_ENCONTRADO", "No existe ese profesional.");
    }

    // Turnos (no bloqueos) que ocupan parte del rango: confirmados, o apartados todavía vigentes (alguien
    // completando sus datos en este momento).
    private List<TurnoEnElRango> turnosEnElRango(Local local, List<UUID> profesionales, Instant desde, Instant hasta, ZoneId zona) {
        if (profesionales.isEmpty()) {
            return List.of();
        }
        return jdbc.query("""
                SELECT t.id, p.nombre AS profesional, t.inicio, c.nombre AS cliente FROM turno t
                JOIN profesional p ON p.local_id = t.local_id AND p.id = t.profesional_id
                LEFT JOIN cliente c ON c.local_id = t.local_id AND c.id = t.cliente_id
                WHERE t.local_id = ? AND t.profesional_id = ANY (?) AND t.tipo <> 'bloqueo'
                  AND (t.estado = 'confirmado' OR (t.estado = 'apartado' AND t.vence_en > ?))
                  AND t.inicio < ? AND t.ocupado_hasta > ?
                ORDER BY t.inicio, p.orden
                """, (fila, n) -> new TurnoEnElRango(fila.getObject("id", UUID.class), fila.getString("profesional"),
                LocalDateTime.ofInstant(fila.getTimestamp("inicio").toInstant(), zona), fila.getString("cliente")),
                local.getId(), profesionales.toArray(UUID[]::new), Timestamp.from(reloj.instant()),
                Timestamp.from(hasta), Timestamp.from(desde));
    }
}
