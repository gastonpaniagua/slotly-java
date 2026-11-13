package ar.com.slotly.admin;

import java.io.IOException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;

import ar.com.slotly.error.SlotlyException;
import ar.com.slotly.local.LocalResolver;

// Imágenes del local en Postgres (D10, V10): el admin sube el logo o la foto de un profesional y recibe la URL
// pública, que se guarda en logo_url o foto_url. Así la web pública las muestra igual que antes.
@RestController
class ImagenesController {

    static final int MAXIMO_BYTES = 512_000;

    private final LocalResolver localResolver;
    private final JdbcTemplate jdbc;
    private final Clock reloj;

    ImagenesController(LocalResolver localResolver, JdbcTemplate jdbc, Clock reloj) {
        this.localResolver = localResolver;
        this.jdbc = jdbc;
        this.reloj = reloj;
    }

    record ImagenSubida(UUID id, String url) {
    }

    @PostMapping(value = "/api/admin/imagenes", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Sube una imagen PNG, JPG o WebP de hasta 500 KB y devuelve su URL pública (D10)")
    ImagenSubida subir(@Parameter(hidden = true) @RequestHeader(HttpHeaders.HOST) String host,
            @RequestParam("archivo") MultipartFile archivo) throws IOException {
        byte[] datos = archivo.getBytes();
        if (datos.length == 0 || datos.length > MAXIMO_BYTES) {
            throw invalida("La imagen puede pesar hasta 500 KB.");
        }
        String tipo = tipoDe(datos).orElseThrow(() -> invalida("La imagen tiene que ser PNG, JPG o WebP."));
        UUID id = jdbc.queryForObject("""
                INSERT INTO imagen (local_id, tipo, datos, creado_en) VALUES (?, ?, ?, ?) RETURNING id
                """, UUID.class, localResolver.resolver(host).getId(), tipo, datos, Timestamp.from(reloj.instant()));
        return new ImagenSubida(id, urlDe(id));
    }

    // Pública: la usan la home y la reserva. Solo del local del subdominio. Como una imagen nunca cambia (otra foto
    // es otro id), el navegador la puede guardar un año sin volver a pedirla.
    @GetMapping("/api/publico/imagenes/{id}")
    @Operation(summary = "Una imagen del local")
    ResponseEntity<byte[]> ver(@Parameter(hidden = true) @RequestHeader(HttpHeaders.HOST) String host, @PathVariable UUID id) {
        List<ResponseEntity<byte[]>> imagenes = jdbc.query("SELECT tipo, datos FROM imagen WHERE id = ? AND local_id = ?",
                (fila, n) -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(fila.getString("tipo")))
                        .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
                        .body(fila.getBytes("datos")),
                id, localResolver.resolver(host).getId());
        if (imagenes.isEmpty()) {
            throw new SlotlyException(HttpStatus.NOT_FOUND, "IMAGEN_NO_ENCONTRADA", "No existe esa imagen.");
        }
        return imagenes.get(0);
    }

    static String urlDe(UUID id) {
        return "/api/publico/imagenes/" + id;
    }

    // El tipo, por los primeros bytes del archivo (la "firma" de cada formato). El nombre o lo que diga el navegador
    // se pueden falsear; el contenido, no tan fácil.
    static Optional<String> tipoDe(byte[] datos) {
        if (empiezaCon(datos, 0, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)) {
            return Optional.of("image/png");
        }
        if (empiezaCon(datos, 0, 0xFF, 0xD8, 0xFF)) {
            return Optional.of("image/jpeg");
        }
        if (empiezaCon(datos, 0, 'R', 'I', 'F', 'F') && empiezaCon(datos, 8, 'W', 'E', 'B', 'P')) {
            return Optional.of("image/webp");
        }
        return Optional.empty();
    }

    private static boolean empiezaCon(byte[] datos, int desde, int... firma) {
        if (datos.length < desde + firma.length) {
            return false;
        }
        byte[] esperado = new byte[firma.length];
        for (int i = 0; i < firma.length; i++) {
            esperado[i] = (byte) firma[i];
        }
        return Arrays.equals(Arrays.copyOfRange(datos, desde, desde + firma.length), esperado);
    }

    private static SlotlyException invalida(String mensaje) {
        return new SlotlyException(HttpStatus.BAD_REQUEST, "VALIDACION", mensaje);
    }
}
