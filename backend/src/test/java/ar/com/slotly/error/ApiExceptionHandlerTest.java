package ar.com.slotly.error;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

// Verifica que el advice convierte las excepciones en ProblemDetail con "codigo" (doc 00).
// @WithMockUser simula un usuario logueado: acá se prueba el formato de errores, no la seguridad.
@WebMvcTest(ApiExceptionHandlerTest.ControladorDePrueba.class)
@Import(ApiExceptionHandlerTest.ControladorDePrueba.class)
@WithMockUser
class ApiExceptionHandlerTest {

    // Controlador que solo existe en este test: cada ruta tira una excepción distinta.
    @RestController
    static class ControladorDePrueba {

        @GetMapping("/prueba/esperado")
        void esperado() {
            throw new SlotlyException(HttpStatus.NOT_FOUND, "LOCAL_NO_ENCONTRADO", "No existe un local con ese subdominio.");
        }

        @GetMapping("/prueba/inesperado")
        void inesperado() {
            throw new IllegalStateException("detalle interno que no debe salir");
        }
    }

    @Autowired
    MockMvc mockMvc;

    @Test
    @DisplayName("Etapa 1: un error esperado responde con su estado, su código y su detalle")
    void errorEsperadoRespondeConSuEstadoYCodigo() throws Exception {
        mockMvc.perform(get("/prueba/esperado"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE))
                .andExpect(jsonPath("$.codigo").value("LOCAL_NO_ENCONTRADO"))
                .andExpect(jsonPath("$.detail").value("No existe un local con ese subdominio."));
    }

    @Test
    @DisplayName("Etapa 1: un error inesperado responde 500 genérico, sin filtrar el detalle interno")
    void errorInesperadoResponde500SinFiltrarElDetalle() throws Exception {
        mockMvc.perform(get("/prueba/inesperado"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.codigo").value("ERROR_INTERNO"))
                .andExpect(jsonPath("$.detail").value("Ocurrió un error inesperado. Probá de nuevo en unos minutos."));
    }

    @Test
    @DisplayName("Etapa 1: una ruta que no existe sigue siendo 404, no 500")
    void rutaQueNoExisteResponde404() throws Exception {
        mockMvc.perform(get("/prueba/no-existe"))
                .andExpect(status().isNotFound());
    }
}