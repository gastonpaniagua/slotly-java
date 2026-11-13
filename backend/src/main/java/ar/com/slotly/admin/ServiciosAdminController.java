package ar.com.slotly.admin;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import ar.com.slotly.error.SlotlyException;
import ar.com.slotly.local.LocalResolver;

// Administrar servicios (HU-24). Todo /api/admin/** es solo para el admin (SecurityConfig: un profesional recibe 403,
// CA5) y del local del subdominio (SesionDelLocalFilter). Cambiar un servicio no toca los turnos ya reservados:
// cada turno tiene copiados su duración y su precio (CA2, doc 03).
@RestController
class ServiciosAdminController {

    private static final String COLUMNAS = "id, nombre, descripcion, duracion_min, precio, limpieza_min, orden, activo";
    private static final RowMapper<ServicioAdmin> FILA = (fila, n) -> new ServicioAdmin(fila.getObject("id", UUID.class),
            fila.getString("nombre"), fila.getString("descripcion"), fila.getInt("duracion_min"), fila.getInt("precio"),
            fila.getObject("limpieza_min", Integer.class), fila.getInt("orden"), fila.getBoolean("activo"));

    private final LocalResolver localResolver;
    private final JdbcTemplate jdbc;

    ServiciosAdminController(LocalResolver localResolver, JdbcTemplate jdbc) {
        this.localResolver = localResolver;
        this.jdbc = jdbc;
    }

    record ServicioAdmin(UUID id, String nombre, @Schema(types = {"string", "null"}) String descripcion,
            int duracionMin, int precio, @Schema(types = {"integer", "null"}) Integer limpiezaMin, int orden,
            boolean activo) {
    }

    // CA1 y CA4: las mismas reglas que la base (V2), con un mensaje por campo para mostrar junto a cada uno.
    record ServicioRequest(
            @NotBlank(message = "Ingresá el nombre.")
            @Size(max = 80, message = "El nombre puede tener hasta 80 caracteres.") String nombre,
            @Schema(types = {"string", "null"})
            @Size(max = 300, message = "La descripción puede tener hasta 300 caracteres.") String descripcion,
            @NotNull(message = "Ingresá la duración.")
            @Min(value = 5, message = "La duración va de 5 a 480 minutos.")
            @Max(value = 480, message = "La duración va de 5 a 480 minutos.")
            @MultiploDeCinco(message = "La duración tiene que ser múltiplo de 5 minutos.") Integer duracionMin,
            @NotNull(message = "Ingresá el precio.")
            @Min(value = 0, message = "El precio no puede ser negativo.")
            @Max(value = 9_999_999, message = "El precio es demasiado alto.") Integer precio,
            @Schema(types = {"integer", "null"})
            @Min(value = 0, message = "La limpieza va de 0 a 30 minutos.")
            @Max(value = 30, message = "La limpieza va de 0 a 30 minutos.") Integer limpiezaMin,
            @NotNull Boolean activo) {
    }

    record OrdenRequest(@NotEmpty List<UUID> ids) {
    }

    @GetMapping("/api/admin/servicios")
    @Operation(summary = "Todos los servicios del local, también los ocultos, en el orden de la home (HU-24)")
    List<ServicioAdmin> listar(@Parameter(hidden = true) @RequestHeader(HttpHeaders.HOST) String host) {
        return jdbc.query("SELECT " + COLUMNAS + " FROM servicio WHERE local_id = ? ORDER BY orden, nombre", FILA,
                localId(host));
    }

    @PostMapping("/api/admin/servicios")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crea un servicio; queda último en el orden (HU-24 CA1)")
    ServicioAdmin crear(@Parameter(hidden = true) @RequestHeader(HttpHeaders.HOST) String host,
            @Valid @RequestBody ServicioRequest pedido) {
        UUID local = localId(host);
        UUID id = jdbc.queryForObject("""
                INSERT INTO servicio (local_id, nombre, descripcion, duracion_min, precio, limpieza_min, orden, activo)
                VALUES (?, ?, ?, ?, ?, ?, (SELECT coalesce(max(orden), 0) + 1 FROM servicio WHERE local_id = ?), ?)
                RETURNING id
                """, UUID.class, local, pedido.nombre().trim(), sinVacio(pedido.descripcion()), pedido.duracionMin(),
                pedido.precio(), pedido.limpiezaMin(), local, pedido.activo());
        return buscar(local, id);
    }

    @PutMapping("/api/admin/servicios/{id}")
    @Operation(summary = "Edita un servicio, también para ocultarlo o mostrarlo (HU-24 CA2 y CA3)")
    ServicioAdmin editar(@Parameter(hidden = true) @RequestHeader(HttpHeaders.HOST) String host,
            @PathVariable UUID id, @Valid @RequestBody ServicioRequest pedido) {
        UUID local = localId(host);
        int cambiados = jdbc.update("""
                UPDATE servicio SET nombre = ?, descripcion = ?, duracion_min = ?, precio = ?, limpieza_min = ?, activo = ?
                WHERE id = ? AND local_id = ?
                """, pedido.nombre().trim(), sinVacio(pedido.descripcion()), pedido.duracionMin(), pedido.precio(),
                pedido.limpiezaMin(), pedido.activo(), id, local);
        if (cambiados == 0) {
            throw noEncontrado();
        }
        return buscar(local, id);
    }

    // El orden en que se muestran en la home y en el paso 1 de la reserva: el de la lista que manda el panel.
    @PostMapping("/api/admin/servicios/orden")
    @Transactional
    @Operation(summary = "Cambia el orden de los servicios: el de la lista de ids (HU-24)")
    List<ServicioAdmin> ordenar(@Parameter(hidden = true) @RequestHeader(HttpHeaders.HOST) String host,
            @Valid @RequestBody OrdenRequest pedido) {
        UUID local = localId(host);
        Integer delLocal = jdbc.queryForObject("SELECT count(*) FROM servicio WHERE local_id = ?", Integer.class, local);
        if (delLocal == null || delLocal != pedido.ids().size() || pedido.ids().stream().distinct().count() != delLocal) {
            throw new SlotlyException(HttpStatus.BAD_REQUEST, "VALIDACION", "El orden tiene que incluir todos los servicios, una vez.");
        }
        for (int i = 0; i < pedido.ids().size(); i++) {
            if (jdbc.update("UPDATE servicio SET orden = ? WHERE id = ? AND local_id = ?", i + 1, pedido.ids().get(i), local) == 0) {
                throw noEncontrado();
            }
        }
        return listar(host);
    }

    private ServicioAdmin buscar(UUID local, UUID id) {
        return jdbc.queryForObject("SELECT " + COLUMNAS + " FROM servicio WHERE id = ? AND local_id = ?", FILA, id, local);
    }

    private UUID localId(String host) {
        return localResolver.resolver(host).getId();
    }

    private static String sinVacio(String texto) {
        return texto == null || texto.isBlank() ? null : texto.trim();
    }

    private static SlotlyException noEncontrado() {
        return new SlotlyException(HttpStatus.NOT_FOUND, "SERVICIO_NO_ENCONTRADO", "No existe ese servicio.");
    }
}
