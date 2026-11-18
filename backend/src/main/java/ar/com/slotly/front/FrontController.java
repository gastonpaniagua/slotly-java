package ar.com.slotly.front;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.util.HtmlUtils;

import ar.com.slotly.error.SlotlyException;
import ar.com.slotly.local.Local;
import ar.com.slotly.local.LocalResolver;

// En producción la app sirve también el front compilado (Etapa 9): una sola dirección para la web y la API, así la
// cookie de la sesión y la de CSRF son del mismo sitio (D5) y no hace falta CORS. Los archivos (/assets/…, el ícono)
// los sirve Spring desde static/; las páginas del front (/, /agenda, /turno/…) son todas el mismo index.html, y el
// front decide qué mostrar.
// HU-03 CA6: WhatsApp e Instagram no corren JavaScript al armar la vista previa de un link; leen el HTML tal cual.
// Por eso acá se reemplazan el título, la descripción y la imagen por los del local del subdominio.
// En desarrollo el front lo sirve Vite y static/ está vacío: estas rutas dan 404.
@Controller
class FrontController {

    // Un tramo de la ruta sin punto (los archivos sí tienen: /assets/index-abc.js, /favicon.svg). El primero, además,
    // no puede ser el de la API ni el de la documentación: un /api/… que no existe no tiene que devolver la página.
    private static final String A = "/{a:(?!api$|v3$|swagger-ui$)[\\w-]+}";
    private static final String B = "/{b:[\\w-]+}";
    private static final String C = "/{c:[\\w-]+}";
    private static final String D = "/{d:[\\w-]+}";

    // Entre estas dos marcas de index.html van los metadatos (ver frontend/index.html).
    static final String DESDE = "<!-- metadatos -->";
    static final String HASTA = "<!-- /metadatos -->";

    private final LocalResolver localResolver;
    private final Optional<String> plantilla;

    FrontController(LocalResolver localResolver, ResourceLoader recursos) throws IOException {
        this.localResolver = localResolver;
        Resource index = recursos.getResource("classpath:/static/index.html");
        this.plantilla = index.exists()
                ? Optional.of(index.getContentAsString(StandardCharsets.UTF_8))
                : Optional.empty();
    }

    // Hasta cuatro tramos: la ruta más larga del front es /turno/:id/reprogramar/dia.
    @GetMapping({"/", A, A + B, A + B + C, A + B + C + D})
    ResponseEntity<String> pagina(@RequestHeader(value = HttpHeaders.HOST, required = false) String host) {
        if (plantilla.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        String html = plantilla.get();
        try {
            html = conMetadatos(html, localResolver.resolver(host));
        } catch (SlotlyException e) {
            // Host sin local: va la página genérica y el front muestra su "no encontrado".
        }
        return ResponseEntity.ok()
                .contentType(new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8))
                // Siempre la versión nueva: después de un deploy, index.html apunta a los /assets/ nuevos.
                .cacheControl(CacheControl.noCache())
                .body(html);
    }

    // Solo los caracteres especiales del HTML (< > & " '); los acentos quedan como están, la página es UTF-8.
    private static String escapar(String texto) {
        return HtmlUtils.htmlEscape(texto, StandardCharsets.UTF_8.name());
    }

    private static String conMetadatos(String html, Local local) {
        int desde = html.indexOf(DESDE);
        int hasta = html.indexOf(HASTA);
        if (desde < 0 || hasta < desde) {
            return html;
        }
        String origen = ServletUriComponentsBuilder.fromCurrentContextPath().toUriString();
        String url = ServletUriComponentsBuilder.fromCurrentRequestUri().toUriString();
        String nombre = escapar(local.getNombre());
        String descripcion = escapar(local.getDescripcion() == null || local.getDescripcion().isBlank()
                ? "Reservá tu turno en " + local.getNombre() + "."
                : local.getDescripcion());
        StringBuilder metadatos = new StringBuilder(DESDE).append('\n')
                .append("    <title>").append(nombre).append("</title>\n")
                .append("    <meta name=\"description\" content=\"").append(descripcion).append("\" />\n")
                .append("    <meta property=\"og:type\" content=\"website\" />\n")
                .append("    <meta property=\"og:site_name\" content=\"").append(nombre).append("\" />\n")
                .append("    <meta property=\"og:title\" content=\"").append(nombre).append("\" />\n")
                .append("    <meta property=\"og:description\" content=\"").append(descripcion).append("\" />\n")
                .append("    <meta property=\"og:url\" content=\"").append(escapar(url)).append("\" />\n");
        // La vista previa necesita la dirección completa de la imagen, no /api/publico/imagenes/….
        if (local.getLogoUrl() != null && !local.getLogoUrl().isBlank()) {
            metadatos.append("    <meta property=\"og:image\" content=\"")
                    .append(escapar(origen + local.getLogoUrl())).append("\" />\n");
        }
        metadatos.append("    <meta name=\"twitter:card\" content=\"summary\" />\n    ");
        return html.substring(0, desde) + metadatos + html.substring(hasta);
    }
}
