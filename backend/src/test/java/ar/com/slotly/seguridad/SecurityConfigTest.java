package ar.com.slotly.seguridad;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
}
