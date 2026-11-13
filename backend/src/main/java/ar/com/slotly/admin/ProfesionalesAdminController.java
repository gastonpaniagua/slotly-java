package ar.com.slotly.admin;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import ar.com.slotly.error.SlotlyException;
import ar.com.slotly.error.ValidacionException;
import ar.com.slotly.local.Local;
import ar.com.slotly.local.LocalResolver;

// Profesionales del local y sus usuarios (HU-25). Solo el admin (/api/admin/**).
@RestController
class ProfesionalesAdminController {

    private final LocalResolver localResolver;
    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final Clock reloj;

    ProfesionalesAdminController(LocalResolver localResolver, JdbcTemplate jdbc, PasswordEncoder encoder, Clock reloj) {
        this.localResolver = localResolver;
        this.jdbc = jdbc;
        this.encoder = encoder;
        this.reloj = reloj;
    }

    // usuarioEmail: el mail con el que entra al panel, o null si todavía no tiene usuario.
    // turnosFuturos: cuántos turnos confirmados tiene por delante (para avisar antes de darlo de baja, CA3).
    record ProfesionalAdmin(UUID id, String nombre, @Schema(types = {"string", "null"}) String fotoUrl, boolean activo,
            List<UUID> servicioIds, @Schema(types = {"string", "null"}) String usuarioEmail, int turnosFuturos) {
    }

    record ProfesionalRequest(
            @NotBlank(message = "Ingresá el nombre.")
            @Size(max = 80, message = "El nombre puede tener hasta 80 caracteres.") String nombre,
            // Solo imágenes subidas al local (ImagenesController); null para sin foto.
            @Schema(types = {"string", "null"})
            @Pattern(regexp = "/api/publico/imagenes/[0-9a-f-]{36}", message = "La foto no es válida.") String fotoUrl,
            @NotNull List<UUID> servicioIds) {
    }

    record UsuarioRequest(
            @NotBlank(message = "Ingresá el mail.") @Size(max = 120, message = "El mail es demasiado largo.")
            @Pattern(regexp = "\\s*[^@\\s]+@[^@\\s]+\\.[^@\\s]+\\s*", message = "Revisá el mail.") String email,
            @NotBlank(message = "Ingresá una contraseña.")
            @Size(min = 8, max = 200, message = "La contraseña tiene que tener al menos 8 caracteres.") String password) {
    }

    record BajaRequest(@NotNull Boolean confirmar) {
    }

    // Un turno futuro del profesional, para la lista de "reprogramar antes de la baja" (CA3). En hora del local.
    record TurnoFuturo(UUID id, @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm")
            @Schema(type = "string", example = "2026-11-17T10:00") LocalDateTime inicio,
            @Schema(types = {"string", "null"}) String clienteNombre) {
    }

    @GetMapping("/api/admin/profesionales")
    @Operation(summary = "Todos los profesionales del local, también los dados de baja (HU-25)")
    List<ProfesionalAdmin> listar(@Parameter(hidden = true) @RequestHeader(HttpHeaders.HOST) String host) {
        UUID local = localResolver.resolver(host).getId();
        return jdbc.queryForList("SELECT id FROM profesional WHERE local_id = ? ORDER BY activo DESC, orden, nombre",
                UUID.class, local).stream().map(id -> buscar(local, id)).toList();
    }

    @PostMapping("/api/admin/profesionales")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    @Operation(summary = "Da de alta un profesional con sus servicios; aparece en la reserva para esos servicios (HU-25 CA1)")
    ProfesionalAdmin crear(@Parameter(hidden = true) @RequestHeader(HttpHeaders.HOST) String host,
            @Valid @RequestBody ProfesionalRequest pedido) {
        UUID local = localResolver.resolver(host).getId();
        UUID id = jdbc.queryForObject("""
                INSERT INTO profesional (local_id, nombre, foto_url, orden)
                VALUES (?, ?, ?, (SELECT coalesce(max(orden), 0) + 1 FROM profesional WHERE local_id = ?))
                RETURNING id
                """, UUID.class, local, pedido.nombre().trim(), pedido.fotoUrl(), local);
        guardarServicios(local, id, pedido.servicioIds());
        return buscar(local, id);
    }

    @PutMapping("/api/admin/profesionales/{id}")
    @Transactional
    @Operation(summary = "Edita nombre, foto y servicios de un profesional (HU-25)")
    ProfesionalAdmin editar(@Parameter(hidden = true) @RequestHeader(HttpHeaders.HOST) String host,
            @PathVariable UUID id, @Valid @RequestBody ProfesionalRequest pedido) {
        UUID local = localResolver.resolver(host).getId();
        if (jdbc.update("UPDATE profesional SET nombre = ?, foto_url = ? WHERE id = ? AND local_id = ?",
                pedido.nombre().trim(), pedido.fotoUrl(), id, local) == 0) {
            throw noEncontrado();
        }
        jdbc.update("DELETE FROM profesional_servicio WHERE profesional_id = ? AND local_id = ?", id, local);
        guardarServicios(local, id, pedido.servicioIds());
        return buscar(local, id);
    }

    // CA2 y CA4: el usuario con el que el profesional entra al panel y ve solo su agenda.
    @PostMapping("/api/admin/profesionales/{id}/usuario")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    @Operation(summary = "Crea el usuario del panel de un profesional, con una contraseña inicial (HU-25 CA2)")
    ProfesionalAdmin crearUsuario(@Parameter(hidden = true) @RequestHeader(HttpHeaders.HOST) String host,
            @PathVariable UUID id, @Valid @RequestBody UsuarioRequest pedido) {
        UUID local = localResolver.resolver(host).getId();
        buscar(local, id);
        try {
            jdbc.update("""
                    INSERT INTO usuario (local_id, email, password_hash, rol, profesional_id) VALUES (?, ?, ?, 'profesional', ?)
                    """, local, pedido.email().trim().toLowerCase(), encoder.encode(pedido.password()), id);
        } catch (DuplicateKeyException e) {
            if (String.valueOf(e.getMessage()).contains("usuario_local_id_email_key")) {
                throw new ValidacionException("email", "Ese mail ya tiene un usuario en este local.");
            }
            throw new SlotlyException(HttpStatus.BAD_REQUEST, "VALIDACION", "Este profesional ya tiene usuario.");
        }
        return buscar(local, id);
    }

    @GetMapping("/api/admin/profesionales/{id}/turnos-futuros")
    @Operation(summary = "Los turnos confirmados por delante de un profesional (HU-25 CA3)")
    List<TurnoFuturo> turnosFuturos(@Parameter(hidden = true) @RequestHeader(HttpHeaders.HOST) String host,
            @PathVariable UUID id) {
        Local local = localResolver.resolver(host);
        buscar(local.getId(), id);
        ZoneId zona = ZoneId.of(local.getZonaHoraria());
        return jdbc.query("""
                SELECT t.id, t.inicio, c.nombre FROM turno t
                LEFT JOIN cliente c ON c.local_id = t.local_id AND c.id = t.cliente_id
                WHERE t.local_id = ? AND t.profesional_id = ? AND t.tipo <> 'bloqueo' AND t.estado = 'confirmado'
                  AND t.inicio > ?
                ORDER BY t.inicio
                """, (fila, n) -> new TurnoFuturo(fila.getObject("id", UUID.class),
                LocalDateTime.ofInstant(fila.getTimestamp("inicio").toInstant(), zona), fila.getString("nombre")),
                local.getId(), id, Timestamp.from(reloj.instant()));
    }

    // CA3: con turnos por delante, la baja necesita confirmación (el panel muestra cuántos y cuáles). Al darlo de
    // baja deja de recibir reservas; sus turnos siguen confirmados (se reprograman o cancelan desde la agenda) y su
    // usuario no puede entrar más: se desactiva y se cierran sus sesiones abiertas.
    @PostMapping("/api/admin/profesionales/{id}/baja")
    @Transactional
    @Operation(summary = "Da de baja un profesional; con turnos futuros pide confirmar (HU-25 CA3)")
    ProfesionalAdmin darDeBaja(@Parameter(hidden = true) @RequestHeader(HttpHeaders.HOST) String host,
            @PathVariable UUID id, @Valid @RequestBody BajaRequest pedido) {
        UUID local = localResolver.resolver(host).getId();
        ProfesionalAdmin profesional = buscar(local, id);
        if (profesional.turnosFuturos() > 0 && !pedido.confirmar()) {
            throw new SlotlyException(HttpStatus.CONFLICT, "TURNOS_FUTUROS",
                    "Tiene " + profesional.turnosFuturos() + " turnos por delante. Confirmá la baja para seguir.");
        }
        jdbc.update("UPDATE profesional SET activo = false WHERE id = ? AND local_id = ?", id, local);
        List<UUID> usuarios = jdbc.queryForList(
                "SELECT id FROM usuario WHERE profesional_id = ? AND local_id = ?", UUID.class, id, local);
        for (UUID usuario : usuarios) {
            jdbc.update("UPDATE usuario SET activo = false WHERE id = ?", usuario);
            jdbc.update("DELETE FROM spring_session WHERE principal_name = ?", usuario.toString());
        }
        return buscar(local, id);
    }

    @PostMapping("/api/admin/profesionales/{id}/alta")
    @Transactional
    @Operation(summary = "Vuelve a dar de alta un profesional dado de baja, con su usuario (HU-25)")
    ProfesionalAdmin darDeAlta(@Parameter(hidden = true) @RequestHeader(HttpHeaders.HOST) String host,
            @PathVariable UUID id) {
        UUID local = localResolver.resolver(host).getId();
        if (jdbc.update("UPDATE profesional SET activo = true WHERE id = ? AND local_id = ?", id, local) == 0) {
            throw noEncontrado();
        }
        jdbc.update("UPDATE usuario SET activo = true WHERE profesional_id = ? AND local_id = ?", id, local);
        return buscar(local, id);
    }

    // Solo servicios de este local (la base igual lo garantiza con la clave compuesta, HU-04).
    private void guardarServicios(UUID local, UUID profesional, List<UUID> servicios) {
        List<UUID> distintos = servicios.stream().distinct().toList();
        Integer delLocal = distintos.isEmpty() ? 0 : jdbc.queryForObject(
                "SELECT count(*) FROM servicio WHERE local_id = ? AND id = ANY (?)", Integer.class, local,
                distintos.toArray(UUID[]::new));
        if (delLocal == null || delLocal != distintos.size()) {
            throw new ValidacionException("servicioIds", "Algún servicio elegido no es de este local.");
        }
        for (UUID servicio : distintos) {
            jdbc.update("INSERT INTO profesional_servicio (local_id, profesional_id, servicio_id) VALUES (?, ?, ?)",
                    local, profesional, servicio);
        }
    }

    private ProfesionalAdmin buscar(UUID local, UUID id) {
        List<ProfesionalAdmin> encontrados = jdbc.query("""
                SELECT p.id, p.nombre, p.foto_url, p.activo,
                       (SELECT u.email FROM usuario u WHERE u.local_id = p.local_id AND u.profesional_id = p.id) AS email,
                       (SELECT count(*) FROM turno t WHERE t.local_id = p.local_id AND t.profesional_id = p.id
                          AND t.tipo <> 'bloqueo' AND t.estado = 'confirmado' AND t.inicio > ?) AS futuros
                FROM profesional p WHERE p.id = ? AND p.local_id = ?
                """, (fila, n) -> new ProfesionalAdmin(fila.getObject("id", UUID.class), fila.getString("nombre"),
                fila.getString("foto_url"), fila.getBoolean("activo"),
                jdbc.queryForList("""
                        SELECT ps.servicio_id FROM profesional_servicio ps
                        JOIN servicio s ON s.local_id = ps.local_id AND s.id = ps.servicio_id
                        WHERE ps.profesional_id = ? ORDER BY s.orden, s.nombre
                        """, UUID.class, fila.getObject("id", UUID.class)),
                fila.getString("email"), fila.getInt("futuros")),
                Timestamp.from(reloj.instant()), id, local);
        if (encontrados.isEmpty()) {
            throw noEncontrado();
        }
        return encontrados.get(0);
    }

    private static SlotlyException noEncontrado() {
        return new SlotlyException(HttpStatus.NOT_FOUND, "PROFESIONAL_NO_ENCONTRADO", "No existe ese profesional.");
    }
}
