package ar.com.slotly.panel;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
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
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import ar.com.slotly.cliente.Telefonos;
import ar.com.slotly.error.SlotlyException;
import ar.com.slotly.error.ValidacionException;
import ar.com.slotly.local.Local;
import ar.com.slotly.local.LocalResolver;
import ar.com.slotly.usuario.UsuarioSesion;

// Turnos desde la agenda del panel: cargar uno manual (HU-28), moverlo y cancelarlo (HU-29). El admin trabaja sobre
// cualquier profesional del local; un profesional, solo sobre su agenda. El constraint de la base (HU-13) es el que
// garantiza que nada se pise.
@RestController
class TurnosPanelController {

    private final LocalResolver localResolver;
    private final JdbcTemplate jdbc;
    private final Clock reloj;

    TurnosPanelController(LocalResolver localResolver, JdbcTemplate jdbc, Clock reloj) {
        this.localResolver = localResolver;
        this.jdbc = jdbc;
        this.reloj = reloj;
    }

    // profesional: el id (si falta, el del usuario). fecha y hora, del local. El teléfono es opcional (CA1).
    record TurnoManualRequest(
            @Schema(types = {"string", "null"}) String profesional,
            @NotNull(message = "Elegí el día.") LocalDate fecha,
            @NotNull(message = "Elegí la hora.") @JsonFormat(pattern = "HH:mm")
            @Schema(type = "string", example = "10:30") LocalTime hora,
            @NotEmpty(message = "Elegí al menos un servicio.") List<UUID> servicioIds,
            @NotBlank(message = "Ingresá el nombre del cliente.")
            @Size(max = 80, message = "El nombre puede tener hasta 80 caracteres.") String clienteNombre,
            @Schema(types = {"string", "null"}) String clienteTelefono) {
    }

    record MoverRequest(
            @NotNull(message = "Elegí el día.") LocalDate fecha,
            @NotNull(message = "Elegí la hora.") @JsonFormat(pattern = "HH:mm")
            @Schema(type = "string", example = "11:00") LocalTime hora) {
    }

    record TurnoGuardado(UUID id) {
    }

    @PostMapping("/api/panel/turnos")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    @Operation(summary = "Carga un turno manual: sin anticipación mínima ni límite de turnos del cliente (HU-28)")
    TurnoGuardado crearManual(@Parameter(hidden = true) @RequestHeader(HttpHeaders.HOST) String host,
            @AuthenticationPrincipal UsuarioSesion usuario, @Valid @RequestBody TurnoManualRequest pedido) {
        Local local = localResolver.resolver(host);
        UUID profesional = profesionalPermitido(local, usuario, pedido.profesional());
        List<Map<String, Object>> servicios = servicios(local, pedido.servicioIds());
        UUID cliente = cliente(local, pedido.clienteNombre().trim(), pedido.clienteTelefono());

        int duracion = servicios.stream().mapToInt(s -> (Integer) s.get("duracion_min")).sum();
        int limpieza = servicios.stream()
                .mapToInt(s -> s.get("limpieza_min") != null ? (Integer) s.get("limpieza_min") : local.getLimpiezaMin())
                .max().orElse(local.getLimpiezaMin());
        Instant inicio = pedido.fecha().atTime(pedido.hora()).atZone(ZoneId.of(local.getZonaHoraria())).toInstant();
        Instant fin = inicio.plus(Duration.ofMinutes(duracion));
        Instant ocupadoHasta = fin.plus(Duration.ofMinutes(limpieza));

        prepararHueco(local, profesional, inicio, ocupadoHasta);
        UUID id = conElConstraint(() -> jdbc.queryForObject("""
                INSERT INTO turno (local_id, profesional_id, cliente_id, tipo, estado, inicio, fin, ocupado_hasta)
                VALUES (?, ?, ?, 'manual', 'confirmado', ?, ?, ?) RETURNING id
                """, UUID.class, local.getId(), profesional, cliente, Timestamp.from(inicio), Timestamp.from(fin),
                Timestamp.from(ocupadoHasta)));
        // Duración y precio copiados, como en toda reserva (doc 03).
        for (Map<String, Object> servicio : servicios) {
            jdbc.update("INSERT INTO turno_servicio (local_id, turno_id, servicio_id, duracion_min, precio) VALUES (?, ?, ?, ?, ?)",
                    local.getId(), id, servicio.get("id"), servicio.get("duracion_min"), servicio.get("precio"));
        }
        return new TurnoGuardado(id);
    }

    // HU-29 CA1 y CA2: el turno pasa al horario nuevo en una sola operación (es la misma fila: el horario viejo queda
    // libre en el mismo momento). Si el nuevo se pisa con algo, HORARIO_OCUPADO y el turno no cambia. Conserva su
    // duración y su limpieza; si tenía link (una reserva web), el link sigue andando y muestra el horario nuevo.
    @PostMapping("/api/panel/turnos/{id}/mover")
    @Transactional
    @Operation(summary = "Mueve un turno a otro día u hora del mismo profesional (HU-29)")
    TurnoGuardado mover(@Parameter(hidden = true) @RequestHeader(HttpHeaders.HOST) String host,
            @AuthenticationPrincipal UsuarioSesion usuario, @PathVariable UUID id, @Valid @RequestBody MoverRequest pedido) {
        Local local = localResolver.resolver(host);
        Map<String, Object> turno = bloquear(local, usuario, id);
        if (!"confirmado".equals(turno.get("estado"))) {
            throw new SlotlyException(HttpStatus.BAD_REQUEST, "VALIDACION", "Solo se puede mover un turno confirmado.");
        }
        Instant inicioViejo = ((Timestamp) turno.get("inicio")).toInstant();
        Duration duracion = Duration.between(inicioViejo, ((Timestamp) turno.get("fin")).toInstant());
        Duration conLimpieza = Duration.between(inicioViejo, ((Timestamp) turno.get("ocupado_hasta")).toInstant());
        Instant inicio = pedido.fecha().atTime(pedido.hora()).atZone(ZoneId.of(local.getZonaHoraria())).toInstant();

        UUID profesional = (UUID) turno.get("profesional_id");
        prepararHueco(local, profesional, inicio, inicio.plus(conLimpieza));
        conElConstraint(() -> jdbc.update("""
                UPDATE turno SET inicio = ?, fin = ?, ocupado_hasta = ?,
                                 token_vence_en = CASE WHEN token_hash IS NULL THEN NULL ELSE ?::timestamptz END
                WHERE id = ?
                """, Timestamp.from(inicio), Timestamp.from(inicio.plus(duracion)), Timestamp.from(inicio.plus(conLimpieza)),
                Timestamp.from(inicio.plus(duracion).plus(Duration.ofDays(30))), id));
        return new TurnoGuardado(id);
    }

    // HU-29 CA3: desde el panel se cancela aunque falte poco (la regla de las 3 horas es para el cliente).
    // Cancelarlo otra vez no cambia nada.
    @PostMapping("/api/panel/turnos/{id}/cancelar")
    @Transactional
    @Operation(summary = "Cancela un turno desde la agenda, sin plazo (HU-29 CA3)")
    TurnoGuardado cancelar(@Parameter(hidden = true) @RequestHeader(HttpHeaders.HOST) String host,
            @AuthenticationPrincipal UsuarioSesion usuario, @PathVariable UUID id) {
        Local local = localResolver.resolver(host);
        Map<String, Object> turno = bloquear(local, usuario, id);
        if ("cancelado".equals(turno.get("estado"))) {
            return new TurnoGuardado(id);
        }
        if (!"confirmado".equals(turno.get("estado"))) {
            throw new SlotlyException(HttpStatus.BAD_REQUEST, "VALIDACION", "Solo se puede cancelar un turno confirmado.");
        }
        jdbc.update("UPDATE turno SET estado = 'cancelado' WHERE id = ?", id);
        return new TurnoGuardado(id);
    }

    // El turno (no un bloqueo), bloqueado hasta el final de la transacción. Uno de otro local, o de otro profesional
    // si quien pide es un profesional, "no existe".
    private Map<String, Object> bloquear(Local local, UsuarioSesion usuario, UUID id) {
        try {
            return usuario.esAdmin()
                    ? jdbc.queryForMap("""
                            SELECT estado, inicio, fin, ocupado_hasta, profesional_id FROM turno
                            WHERE id = ? AND local_id = ? AND tipo <> 'bloqueo' FOR UPDATE
                            """, id, local.getId())
                    : jdbc.queryForMap("""
                            SELECT estado, inicio, fin, ocupado_hasta, profesional_id FROM turno
                            WHERE id = ? AND local_id = ? AND tipo <> 'bloqueo' AND profesional_id = ? FOR UPDATE
                            """, id, local.getId(), usuario.profesionalId());
        } catch (EmptyResultDataAccessException e) {
            throw new SlotlyException(HttpStatus.NOT_FOUND, "TURNO_NO_ENCONTRADO", "No existe ese turno.");
        }
    }

    private UUID profesionalPermitido(Local local, UsuarioSesion usuario, String pedido) {
        if (!usuario.esAdmin()) {
            if (pedido != null && !pedido.equals(String.valueOf(usuario.profesionalId()))) {
                throw new SlotlyException(HttpStatus.FORBIDDEN, "SIN_PERMISO", "Solo podés cargar turnos en tu agenda.");
            }
            return usuario.profesionalId();
        }
        if (pedido == null && usuario.profesionalId() != null) {
            return usuario.profesionalId();
        }
        try {
            UUID id = UUID.fromString(String.valueOf(pedido));
            Integer existe = jdbc.queryForObject("SELECT count(*) FROM profesional WHERE id = ? AND local_id = ?",
                    Integer.class, id, local.getId());
            if (existe != null && existe > 0) {
                return id;
            }
        } catch (IllegalArgumentException e) {
            // cae al 404
        }
        throw new SlotlyException(HttpStatus.NOT_FOUND, "PROFESIONAL_NO_ENCONTRADO", "No existe ese profesional.");
    }

    // Servicios activos del local, con lo que se copia al turno.
    private List<Map<String, Object>> servicios(Local local, List<UUID> ids) {
        List<UUID> distintos = ids.stream().distinct().toList();
        List<Map<String, Object>> servicios = jdbc.queryForList("""
                SELECT id, duracion_min, precio, limpieza_min FROM servicio WHERE local_id = ? AND activo AND id = ANY (?)
                """, local.getId(), distintos.toArray(UUID[]::new));
        if (servicios.size() != distintos.size()) {
            throw new ValidacionException("servicioIds", "Algún servicio elegido no está disponible.");
        }
        return servicios;
    }

    // CA1: con teléfono, el cliente de ese teléfono en el local (o uno nuevo); sin teléfono, un cliente nuevo solo con
    // el nombre (doc 03: el teléfono es opcional solo en los turnos manuales).
    private UUID cliente(Local local, String nombre, String telefonoEscrito) {
        if (telefonoEscrito == null || telefonoEscrito.isBlank()) {
            return jdbc.queryForObject("INSERT INTO cliente (local_id, nombre) VALUES (?, ?) RETURNING id",
                    UUID.class, local.getId(), nombre);
        }
        String telefono = Telefonos.normalizar(telefonoEscrito)
                .orElseThrow(() -> new ValidacionException("clienteTelefono", "Revisá el número de celular."));
        return jdbc.queryForObject("""
                INSERT INTO cliente (local_id, nombre, telefono) VALUES (?, ?, ?)
                ON CONFLICT (local_id, telefono) DO UPDATE SET nombre = cliente.nombre
                RETURNING id
                """, UUID.class, local.getId(), nombre, telefono);
    }

    // El candado por profesional (como la reserva) y los apartados vencidos que todavía bloquean, fuera (doc 03).
    private void prepararHueco(Local local, UUID profesional, Instant inicio, Instant ocupadoHasta) {
        jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?::text, 0))", profesional);
        jdbc.update("""
                UPDATE turno SET estado = 'vencido'
                WHERE local_id = ? AND profesional_id = ? AND estado = 'apartado' AND vence_en <= ?
                  AND inicio < ? AND ocupado_hasta > ?
                """, local.getId(), profesional, Timestamp.from(reloj.instant()), Timestamp.from(ocupadoHasta),
                Timestamp.from(inicio));
    }

    // CA2 de las dos historias: si se pisa con un turno o un bloqueo, la base lo frena y se responde HORARIO_OCUPADO.
    private static <T> T conElConstraint(Supplier<T> operacion) {
        try {
            return operacion.get();
        } catch (DataIntegrityViolationException | PessimisticLockingFailureException e) {
            if (e instanceof PessimisticLockingFailureException || String.valueOf(e.getMessage()).contains("turno_sin_superposicion")) {
                throw new SlotlyException(HttpStatus.CONFLICT, "HORARIO_OCUPADO", "Ese horario se pisa con otro turno o un bloqueo.");
            }
            throw e;
        }
    }
}
