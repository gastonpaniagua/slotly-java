package ar.com.slotly.seguridad;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import ar.com.slotly.IntegrationTest;

// Verifica que lo que no es público pide login y el 401 sale con nuestro formato.
@IntegrationTest
class SecurityConfigTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    @DisplayName("Etapa 1: una ruta que no es pública, sin login, responde 401 NO_AUTENTICADO")
    void rutaPrivadaSinLoginResponde401() throws Exception {
        mockMvc.perform(get("/api/privado"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE))
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"))
                .andExpect(jsonPath("$.detail").value("Tenés que iniciar sesión."));
    }

    @Test
    @DisplayName("Etapa 4: un POST público sin token CSRF no lo frena la seguridad (llega a Spring MVC)")
    void postPublicoSinCsrf() throws Exception {
        // /api/publico/local solo acepta GET: que responda 405 (y no 403) prueba que CSRF no lo bloqueó.
        mockMvc.perform(post("/api/publico/local"))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    @DisplayName("Etapa 4: fuera de lo público, un POST sin token CSRF se rechaza")
    void postPrivadoSinCsrf() throws Exception {
        mockMvc.perform(post("/api/privado"))
                .andExpect(status().isForbidden());
    }
}
