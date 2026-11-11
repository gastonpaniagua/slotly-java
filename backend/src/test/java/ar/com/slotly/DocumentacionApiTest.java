package ar.com.slotly;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

// La documentación OpenAPI se genera, es pública y describe la home (de ella salen los tipos del front).
@IntegrationTest
class DocumentacionApiTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    @DisplayName("Etapa 1: /v3/api-docs responde sin login y documenta GET /api/publico/local")
    void documentaLaApiPublica() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/publico/local'].get").exists())
                .andExpect(jsonPath("$.components.schemas.LocalPublicoResponse").exists())
                .andExpect(jsonPath("$.paths['/api/publico/local'].get.parameters").doesNotExist());
    }
}
