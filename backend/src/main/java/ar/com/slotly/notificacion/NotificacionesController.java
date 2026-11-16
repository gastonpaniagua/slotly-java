package ar.com.slotly.notificacion;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.annotation.JsonFormat;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;

import ar.com.slotly.usuario.UsuarioSesion;

// Los avisos del profesional en el panel (HU-37): el contador de no leídas y la lista. Cada uno ve solo los suyos
// (CA4): se buscan por el profesional de la sesión, nunca por uno que venga en el pedido. Un admin sin agenda propia
// no tiene avisos.
@RestController
class NotificacionesController {

    // Las últimas: alcanza para ver qué pasó en estos días sin traer el historial entero.
    static final int MAXIMO = 30;

    private final JdbcTemplate jdbc;
    private final Clock reloj;

    NotificacionesController(JdbcTemplate jdbc, Clock reloj) {
        this.jdbc = jdbc;
        this.reloj = reloj;
    }

    // tipo: reserva, cancelacion, reprogramacion o movido. fecha y hora: del turno como quedó, en hora del local;
    // fechaAnterior y horaAnterior: cuándo era antes (solo en reprogramacion y movido).
    record Notificacion(UUID id, String tipo, LocalDate fecha, @JsonFormat(pattern = "HH:mm") LocalTime hora,
            @Schema(types = {"string", "null"}, format = "date") LocalDate fechaAnterior,
            @Schema(types = {"string", "null"}, example = "10:00") @JsonFormat(pattern = "HH:mm") LocalTime horaAnterior,
            String clienteNombre, List<String> servicios,
            @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm") @Schema(type = "string", example = "2026-11-16T09:30") LocalDateTime creadaEn,
            boolean leida) {
    }

    record Bandeja(int sinLeer, List<Notificacion> notificaciones) {
    }

    @GetMapping("/api/panel/notificaciones")
    @Operation(summary = "Los avisos del profesional de la sesión, del más nuevo al más viejo, y cuántos no leyó (HU-37)")
    Bandeja ver(@AuthenticationPrincipal UsuarioSesion usuario) {
        if (usuario.profesionalId() == null) {
            return new Bandeja(0, List.of());
        }
        ZoneId zona = ZoneId.of(jdbc.queryForObject("SELECT zona_horaria FROM local WHERE id = ?", String.class, usuario.localId()));
        Integer sinLeer = jdbc.queryForObject("""
                SELECT count(*) FROM notificacion WHERE local_id = ? AND profesional_id = ? AND leida_en IS NULL
                """, Integer.class, usuario.localId(), usuario.profesionalId());
        List<Notificacion> notificaciones = jdbc.query("""
                SELECT n.id, n.tipo, t.inicio, n.inicio_anterior, c.nombre AS cliente, n.creada_en, n.leida_en IS NOT NULL AS leida,
                       ARRAY(SELECT s.nombre FROM turno_servicio ts JOIN servicio s ON s.local_id = ts.local_id AND s.id = ts.servicio_id
                             WHERE ts.turno_id = t.id ORDER BY s.orden, s.nombre) AS servicios
                FROM notificacion n
                JOIN turno t ON t.local_id = n.local_id AND t.id = n.turno_id
                LEFT JOIN cliente c ON c.local_id = t.local_id AND c.id = t.cliente_id
                WHERE n.local_id = ? AND n.profesional_id = ?
                ORDER BY n.creada_en DESC, n.id
                LIMIT ?
                """, (fila, n) -> {
                    LocalDateTime inicio = LocalDateTime.ofInstant(fila.getTimestamp("inicio").toInstant(), zona);
                    Timestamp anterior = fila.getTimestamp("inicio_anterior");
                    LocalDateTime antes = anterior == null ? null : LocalDateTime.ofInstant(anterior.toInstant(), zona);
                    return new Notificacion(fila.getObject("id", UUID.class), fila.getString("tipo"),
                            inicio.toLocalDate(), inicio.toLocalTime(),
                            antes == null ? null : antes.toLocalDate(), antes == null ? null : antes.toLocalTime(),
                            fila.getString("cliente"), Arrays.asList((String[]) fila.getArray("servicios").getArray()),
                            LocalDateTime.ofInstant(fila.getTimestamp("creada_en").toInstant(), zona), fila.getBoolean("leida"));
                }, usuario.localId(), usuario.profesionalId(), MAXIMO);
        return new Bandeja(sinLeer == null ? 0 : sinLeer, notificaciones);
    }

    // CA2: al abrir la lista, todas pasan a leídas y el contador vuelve a cero.
    @PostMapping("/api/panel/notificaciones/leidas")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Marca como leídos todos los avisos del profesional de la sesión (HU-37 CA2)")
    void marcarLeidas(@AuthenticationPrincipal UsuarioSesion usuario) {
        if (usuario.profesionalId() == null) {
            return;
        }
        jdbc.update("""
                UPDATE notificacion SET leida_en = ? WHERE local_id = ? AND profesional_id = ? AND leida_en IS NULL
                """, Timestamp.from(reloj.instant()), usuario.localId(), usuario.profesionalId());
    }
}
