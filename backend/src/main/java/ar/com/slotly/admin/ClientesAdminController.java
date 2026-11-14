package ar.com.slotly.admin;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.annotation.JsonFormat;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;

import ar.com.slotly.error.SlotlyException;
import ar.com.slotly.local.Local;
import ar.com.slotly.local.LocalResolver;

// Los clientes del local (HU-31) y el borrado de sus datos personales (HU-34). Solo el admin (/api/admin/**: un
// profesional recibe 403, HU-31 CA3). Visitas y faltas no se guardan: se cuentan de los turnos (doc 03).
@RestController
class ClientesAdminController {

    static final String NOMBRE_BORRADO = "Cliente borrado";

    private final LocalResolver localResolver;
    private final JdbcTemplate jdbc;
    private final Clock reloj;

    ClientesAdminController(LocalResolver localResolver, JdbcTemplate jdbc, Clock reloj) {
        this.localResolver = localResolver;
        this.jdbc = jdbc;
        this.reloj = reloj;
    }

    // visitas: turnos en los que vino; faltas: en los que no vino; ultimaVisita: el día de la última vez que vino.
    record ClienteAdmin(UUID id, String nombre, @Schema(types = {"string", "null"}) String telefono,
            @Schema(types = {"string", "null"}) String email, int visitas, int faltas,
            @Schema(types = {"string", "null"}, format = "date") LocalDate ultimaVisita, int turnosFuturos) {
    }

    record TurnoDelHistorial(UUID id,
            @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm") @Schema(type = "string", example = "2026-11-17T10:00") LocalDateTime inicio,
            String estado, String profesionalNombre, List<String> servicios, int precioTotal) {
    }

    record ClienteConHistorial(ClienteAdmin cliente, List<TurnoDelHistorial> turnos) {
    }

    @GetMapping("/api/admin/clientes")
    @Operation(summary = "Clientes del local con visitas, faltas y última visita; se busca por nombre o teléfono (HU-31 CA1)")
    List<ClienteAdmin> listar(@Parameter(hidden = true) @RequestHeader(HttpHeaders.HOST) String host,
            @RequestParam(required = false) String buscar) {
        Local local = localResolver.resolver(host);
        String texto = buscar == null ? "" : buscar.trim();
        // Los números se buscan sin espacios ni guiones: "2345-6789" encuentra +5491123456789.
        String digitos = texto.replaceAll("\\D", "");
        List<UUID> ids = jdbc.queryForList("""
                SELECT id FROM cliente
                WHERE local_id = ?
                  AND (? = '' OR nombre ILIKE '%' || ? || '%' OR (? <> '' AND telefono LIKE '%' || ? || '%'))
                ORDER BY lower(nombre), id
                LIMIT 500
                """, UUID.class, local.getId(), texto, texto, digitos, digitos);
        return ids.stream().map(id -> cliente(local, id)).toList();
    }

    @GetMapping("/api/admin/clientes/{id}")
    @Operation(summary = "Un cliente con su historial de turnos en el local, del más nuevo al más viejo (HU-31 CA2)")
    ClienteConHistorial ver(@Parameter(hidden = true) @RequestHeader(HttpHeaders.HOST) String host, @PathVariable UUID id) {
        Local local = localResolver.resolver(host);
        ClienteAdmin cliente = cliente(local, id);
        ZoneId zona = ZoneId.of(local.getZonaHoraria());
        List<TurnoDelHistorial> turnos = jdbc.query("""
                SELECT t.id, t.inicio, t.estado, p.nombre AS profesional,
                       ARRAY(SELECT s.nombre FROM turno_servicio ts JOIN servicio s ON s.local_id = ts.local_id AND s.id = ts.servicio_id
                             WHERE ts.turno_id = t.id ORDER BY s.orden, s.nombre) AS servicios,
                       (SELECT coalesce(sum(ts.precio), 0) FROM turno_servicio ts WHERE ts.turno_id = t.id) AS total
                FROM turno t JOIN profesional p ON p.local_id = t.local_id AND p.id = t.profesional_id
                WHERE t.local_id = ? AND t.cliente_id = ? AND t.tipo <> 'bloqueo'
                  AND t.estado IN ('confirmado', 'completado', 'ausente', 'cancelado')
                ORDER BY t.inicio DESC
                """, (fila, n) -> new TurnoDelHistorial(fila.getObject("id", UUID.class),
                LocalDateTime.ofInstant(fila.getTimestamp("inicio").toInstant(), zona), fila.getString("estado"),
                fila.getString("profesional"), Arrays.asList((String[]) fila.getArray("servicios").getArray()),
                fila.getInt("total")), local.getId(), id);
        return new ClienteConHistorial(cliente, turnos);
    }

    // HU-34: borra los datos personales de un cliente que lo pide (Ley 25.326). Los turnos quedan, sin datos personales,
    // para no romper el historial del local (CA1). Con turnos por delante, primero hay que cancelarlos (CA2).
    @PostMapping("/api/admin/clientes/{id}/borrar-datos")
    @Transactional
    @Operation(summary = "Anonimiza los datos personales de un cliente sin turnos por delante (HU-34)")
    ClienteAdmin borrarDatos(@Parameter(hidden = true) @RequestHeader(HttpHeaders.HOST) String host, @PathVariable UUID id) {
        Local local = localResolver.resolver(host);
        ClienteAdmin cliente = cliente(local, id);
        if (cliente.turnosFuturos() > 0) {
            throw new SlotlyException(HttpStatus.CONFLICT, "TURNOS_FUTUROS", "Tiene " + cliente.turnosFuturos()
                    + (cliente.turnosFuturos() == 1 ? " turno" : " turnos") + " por delante. Cancelalos antes de borrar sus datos.");
        }
        // Lo que lo identificaba fuera de los datos: sus códigos pedidos y sus accesos (HU-22).
        if (cliente.telefono() != null) {
            jdbc.update("DELETE FROM codigo_verificacion WHERE local_id = ? AND telefono = ?", local.getId(), cliente.telefono());
        }
        jdbc.update("DELETE FROM acceso_cliente WHERE local_id = ? AND cliente_id = ?", local.getId(), id);
        jdbc.update("""
                UPDATE cliente SET nombre = ?, telefono = NULL, email = NULL, consentimiento_en = NULL,
                                   ultima_resena_pedida_en = NULL
                WHERE id = ? AND local_id = ?
                """, NOMBRE_BORRADO, id, local.getId());
        return cliente(local, id);
    }

    private ClienteAdmin cliente(Local local, UUID id) {
        ZoneId zona = ZoneId.of(local.getZonaHoraria());
        List<ClienteAdmin> encontrados = jdbc.query("""
                SELECT c.id, c.nombre, c.telefono, c.email,
                       count(*) FILTER (WHERE t.estado = 'completado') AS visitas,
                       count(*) FILTER (WHERE t.estado = 'ausente') AS faltas,
                       max(t.inicio) FILTER (WHERE t.estado = 'completado') AS ultima,
                       count(*) FILTER (WHERE t.estado = 'confirmado' AND t.inicio > ?) AS futuros
                FROM cliente c LEFT JOIN turno t ON t.local_id = c.local_id AND t.cliente_id = c.id AND t.tipo <> 'bloqueo'
                WHERE c.id = ? AND c.local_id = ?
                GROUP BY c.id
                """, (fila, n) -> {
                    Timestamp ultima = fila.getTimestamp("ultima");
                    return new ClienteAdmin(fila.getObject("id", UUID.class), fila.getString("nombre"),
                            fila.getString("telefono"), fila.getString("email"), fila.getInt("visitas"), fila.getInt("faltas"),
                            ultima == null ? null : LocalDate.ofInstant(ultima.toInstant(), zona), fila.getInt("futuros"));
                }, Timestamp.from(reloj.instant()), id, local.getId());
        if (encontrados.isEmpty()) {
            throw new SlotlyException(HttpStatus.NOT_FOUND, "CLIENTE_NO_ENCONTRADO", "No existe ese cliente.");
        }
        return encontrados.get(0);
    }
}
