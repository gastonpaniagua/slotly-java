package ar.com.slotly.admin;

import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import ar.com.slotly.cliente.Telefono;
import ar.com.slotly.cliente.Telefonos;
import ar.com.slotly.local.LocalResolver;

// Datos, imagen y reglas de turnos del local (HU-32). Solo el admin. La web pública y la disponibilidad leen el local
// en cada pedido, así que lo que se guarda acá vale en el acto (CA1 y CA2). Los rangos son los mismos que la base (V1).
@RestController
class LocalAdminController {

    private final LocalResolver localResolver;
    private final JdbcTemplate jdbc;

    LocalAdminController(LocalResolver localResolver, JdbcTemplate jdbc) {
        this.localResolver = localResolver;
        this.jdbc = jdbc;
    }

    // Lo opcional puede venir vacío o null (se guarda null). Las redes, como usuario (sin la URL ni la @).
    record ConfiguracionDelLocal(
            @NotBlank(message = "Ingresá el nombre del local.")
            @Size(max = 80, message = "El nombre puede tener hasta 80 caracteres.") String nombre,
            @Schema(types = {"string", "null"}) @Size(max = 300, message = "La descripción puede tener hasta 300 caracteres.") String descripcion,
            @Schema(types = {"string", "null"})
            @Pattern(regexp = "|/api/publico/imagenes/[0-9a-f-]{36}", message = "El logo no es válido.") String logoUrl,
            @Schema(types = {"string", "null"})
            @Pattern(regexp = "|#[0-9a-fA-F]{6}", message = "El color tiene que ser como #2e7d32.") String colorAcento,
            @Schema(types = {"string", "null"}) @Telefono String whatsapp,
            @Schema(types = {"string", "null"})
            @Pattern(regexp = "|@?[a-zA-Z0-9._]{1,30}", message = "Revisá el usuario de Instagram.") String instagram,
            @Schema(types = {"string", "null"})
            @Pattern(regexp = "|@?[a-zA-Z0-9._]{1,30}", message = "Revisá el usuario de TikTok.") String tiktok,
            @Schema(types = {"string", "null"})
            @Pattern(regexp = "|[a-zA-Z0-9.]{1,50}", message = "Revisá el usuario de Facebook.") String facebook,
            @Schema(types = {"string", "null"}) @Size(max = 120, message = "La dirección puede tener hasta 120 caracteres.") String direccion,
            @Schema(types = {"string", "null"}) @Size(max = 80, message = "La ciudad puede tener hasta 80 caracteres.") String ciudad,
            @Schema(types = {"string", "null"}) @Size(max = 80, message = "El alias puede tener hasta 80 caracteres.") String alias,
            @Schema(types = {"string", "null"}) @Size(max = 80, message = "El titular puede tener hasta 80 caracteres.") String titularCuenta,
            @Schema(types = {"string", "null"})
            @Pattern(regexp = "|[0-9]{22}", message = "El CBU tiene 22 números.") String cbu,
            @Schema(types = {"string", "null"})
            @Pattern(regexp = "|https://\\S{1,300}", message = "El link de reseñas tiene que empezar con https://.") String linkResena,
            // Reglas de turnos (doc 03), CA2 y CA3.
            @NotNull @Min(value = 5, message = "El intervalo va de 5 a 60 minutos.")
            @Max(value = 60, message = "El intervalo va de 5 a 60 minutos.") Integer intervaloMin,
            @NotNull @Min(value = 0, message = "La limpieza va de 0 a 30 minutos.")
            @Max(value = 30, message = "La limpieza va de 0 a 30 minutos.") Integer limpiezaMin,
            @NotNull @Min(value = 0, message = "La anticipación va de 0 a 1440 minutos (24 horas).")
            @Max(value = 1440, message = "La anticipación va de 0 a 1440 minutos (24 horas).") Integer anticipacionMin,
            @NotNull @Min(value = 1, message = "El tope va de 1 a 365 días.")
            @Max(value = 365, message = "El tope va de 1 a 365 días.") Integer topeDias,
            @NotNull @Min(value = 0, message = "Las horas para cancelar van de 0 a 168 (una semana).")
            @Max(value = 168, message = "Las horas para cancelar van de 0 a 168 (una semana).") Integer cancelarHastaHoras,
            @NotNull @Min(value = 1, message = "Los turnos por teléfono van de 1 a 10.")
            @Max(value = 10, message = "Los turnos por teléfono van de 1 a 10.") Integer maxTurnosFuturos) {
    }

    private static final String COLUMNAS = """
            nombre, descripcion, logo_url, color_acento, whatsapp, instagram, tiktok, facebook, direccion, ciudad,
            alias, titular_cuenta, cbu, link_resena, intervalo_min, limpieza_min, anticipacion_min, tope_dias,
            cancelar_hasta_horas, max_turnos_futuros
            """;

    @GetMapping("/api/admin/local")
    @Operation(summary = "La configuración del local: datos, imagen, pago y reglas de turnos (HU-32)")
    ConfiguracionDelLocal ver(@Parameter(hidden = true) @RequestHeader(HttpHeaders.HOST) String host) {
        return buscar(localResolver.resolver(host).getId());
    }

    @PutMapping("/api/admin/local")
    @Operation(summary = "Guarda la configuración del local; la web y las reservas nuevas la usan en el acto (HU-32)")
    ConfiguracionDelLocal guardar(@Parameter(hidden = true) @RequestHeader(HttpHeaders.HOST) String host,
            @Valid @RequestBody ConfiguracionDelLocal pedido) {
        UUID local = localResolver.resolver(host).getId();
        jdbc.update("""
                UPDATE local SET nombre = ?, descripcion = ?, logo_url = ?, color_acento = ?, whatsapp = ?, instagram = ?,
                                 tiktok = ?, facebook = ?, direccion = ?, ciudad = ?, alias = ?, titular_cuenta = ?, cbu = ?,
                                 link_resena = ?, intervalo_min = ?, limpieza_min = ?, anticipacion_min = ?, tope_dias = ?,
                                 cancelar_hasta_horas = ?, max_turnos_futuros = ?
                WHERE id = ?
                """, pedido.nombre().trim(), texto(pedido.descripcion()), texto(pedido.logoUrl()),
                texto(pedido.colorAcento()) == null ? null : pedido.colorAcento().trim().toLowerCase(),
                pedido.whatsapp() == null || pedido.whatsapp().isBlank() ? null : Telefonos.normalizar(pedido.whatsapp()).orElseThrow(),
                usuario(pedido.instagram()), usuario(pedido.tiktok()), texto(pedido.facebook()), texto(pedido.direccion()),
                texto(pedido.ciudad()), texto(pedido.alias()), texto(pedido.titularCuenta()), texto(pedido.cbu()),
                texto(pedido.linkResena()), pedido.intervaloMin(), pedido.limpiezaMin(), pedido.anticipacionMin(),
                pedido.topeDias(), pedido.cancelarHastaHoras(), pedido.maxTurnosFuturos(), local);
        return buscar(local);
    }

    private ConfiguracionDelLocal buscar(UUID local) {
        return jdbc.queryForObject("SELECT " + COLUMNAS + " FROM local WHERE id = ?", (fila, n) -> new ConfiguracionDelLocal(
                fila.getString("nombre"), fila.getString("descripcion"), fila.getString("logo_url"), fila.getString("color_acento"),
                fila.getString("whatsapp"), fila.getString("instagram"), fila.getString("tiktok"), fila.getString("facebook"),
                fila.getString("direccion"), fila.getString("ciudad"), fila.getString("alias"), fila.getString("titular_cuenta"),
                fila.getString("cbu"), fila.getString("link_resena"), fila.getInt("intervalo_min"), fila.getInt("limpieza_min"),
                fila.getInt("anticipacion_min"), fila.getInt("tope_dias"), fila.getInt("cancelar_hasta_horas"),
                fila.getInt("max_turnos_futuros")), local);
    }

    private static String texto(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    // Las redes se guardan como usuario: "@barberia.demo" y "barberia.demo" son lo mismo.
    private static String usuario(String valor) {
        String limpio = texto(valor);
        return limpio == null ? null : limpio.replaceFirst("^@", "");
    }
}
