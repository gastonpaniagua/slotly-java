package ar.com.slotly.front;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import ar.com.slotly.BaseDeTest;
import ar.com.slotly.IntegrationTest;

// La app sirve el front (Etapa 9) con los metadatos del local para la vista previa del link (HU-03 CA6). Usa el
// index.html de prueba de src/test/resources/static.
@IntegrationTest
class FrontControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    UUID imagenId = UUID.fromString("00000000-0000-0000-0000-0000000000aa");

    @BeforeEach
    void preparar() {
        BaseDeTest.vaciar(jdbc);
        jdbc.update("""
                INSERT INTO local (nombre, subdominio, descripcion, logo_url) VALUES
                ('Barbería "La Esquina" & Co', 'demo', 'Cortes <clásicos> y barba.', ?),
                ('Otra', 'otra', NULL, NULL)
                """, "/api/publico/imagenes/" + imagenId);
    }

    @Test
    @DisplayName("HU-03 CA6: la página trae el nombre, la descripción y el logo del local, con la dirección completa")
    void metadatosDelLocal() throws Exception {
        mockMvc.perform(get("/").header(HttpHeaders.HOST, "demo.localhost"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/html"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-cache"))
                // Escapado: un nombre con comillas o < no rompe el HTML.
                .andExpect(content().string(containsString("<title>Barbería &quot;La Esquina&quot; &amp; Co</title>")))
                .andExpect(content().string(containsString(
                        "<meta property=\"og:title\" content=\"Barbería &quot;La Esquina&quot; &amp; Co\" />")))
                .andExpect(content().string(containsString(
                        "<meta name=\"description\" content=\"Cortes &lt;clásicos&gt; y barba.\" />")))
                .andExpect(content().string(containsString(
                        "<meta property=\"og:description\" content=\"Cortes &lt;clásicos&gt; y barba.\" />")))
                .andExpect(content().string(containsString(
                        "<meta property=\"og:image\" content=\"http://demo.localhost/api/publico/imagenes/" + imagenId + "\" />")))
                .andExpect(content().string(containsString("<meta property=\"og:url\" content=\"http://demo.localhost/\" />")))
                // Los genéricos ya no están; el resto de la página, sí.
                .andExpect(content().string(not(containsString("<title>Slotly</title>"))))
                .andExpect(content().string(not(containsString("Reservá tu turno online."))))
                .andExpect(content().string(containsString("<div id=\"root\"></div>")));
    }

    @Test
    @DisplayName("HU-03 CA6: sin descripción ni logo, una descripción armada con el nombre y sin imagen")
    void sinDescripcionNiLogo() throws Exception {
        mockMvc.perform(get("/").header(HttpHeaders.HOST, "otra.localhost"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<title>Otra</title>")))
                .andExpect(content().string(containsString(
                        "<meta property=\"og:description\" content=\"Reservá tu turno en Otra.\" />")))
                .andExpect(content().string(not(containsString("og:image"))));
    }

    @Test
    @DisplayName("Etapa 9: cualquier página del front es el mismo index.html, sin sesión (la API protege los datos)")
    void paginasDelFront() throws Exception {
        for (String ruta : new String[] {"/agenda", "/admin/servicios", "/reservar/servicios",
                "/turno/" + UUID.randomUUID() + "/reprogramar/dia"}) {
            mockMvc.perform(get(ruta).header(HttpHeaders.HOST, "demo.localhost"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("<div id=\"root\"></div>")))
                    .andExpect(content().string(containsString("<meta property=\"og:url\" content=\"http://demo.localhost" + ruta + "\" />")));
        }
    }

    @Test
    @DisplayName("Etapa 9: un host sin local recibe la página genérica, y el front muestra su 'no encontrado'")
    void hostSinLocal() throws Exception {
        mockMvc.perform(get("/").header(HttpHeaders.HOST, "noexiste.localhost"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<title>Slotly</title>")))
                .andExpect(content().string(not(containsString("og:title"))));
    }

    @Test
    @DisplayName("Etapa 9: la API, la documentación y los archivos no devuelven la página")
    void noTapaLaApiNiLosArchivos() throws Exception {
        // Un /api/… que no existe sigue pidiendo sesión, como antes.
        mockMvc.perform(get("/api/no-existe").header(HttpHeaders.HOST, "demo.localhost"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/publico/no-existe/algo").header(HttpHeaders.HOST, "demo.localhost"))
                .andExpect(content().string(not(containsString("<div id=\"root\"></div>"))));
        mockMvc.perform(get("/assets/index-abc123.js").header(HttpHeaders.HOST, "demo.localhost"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/v3/api-docs").header(HttpHeaders.HOST, "demo.localhost"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"));
    }
}
