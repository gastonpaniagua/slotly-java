package ar.com.slotly.error;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Verifica que el advice convierte las excepciones en ProblemDetail con "codigo" (doc 00).
// @WithMockUser simula un usuario logueado: acá se prueba el formato de errores, no la seguridad.
@WebMvcTest(ApiExceptionHandlerTest.ControladorDePrueba.class)
@Import(ApiExceptionHandlerTest.ControladorDePrueba.class)
@WithMockUser
class ApiExceptionHandlerTest {

    // Un cuerpo de prueba con validaciones, como el de una reserva.
    record DatosDePrueba(@NotBlank(message = "Ingresá tu nombre.") @Size(max = 80) String nombre, int edad) {
    }

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

        @PostMapping("/prueba/datos")
        String datos(@Valid @RequestBody DatosDePrueba datos) {
            return "ok";
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

    @Test
    @DisplayName("HU-10 CA4: un campo inválido responde 400 VALIDACION con el campo y su mensaje")
    void campoInvalido() throws Exception {
        enviar("""
                {"nombre": "", "edad": 30}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.errores[0].campo").value("nombre"))
                .andExpect(jsonPath("$.errores[0].mensaje").value("Ingresá tu nombre."));
    }

    @Test
    @DisplayName("HU-15 CA3: un campo que no existe en el contrato se rechaza con 400, no se ignora")
    void campoQueNoExiste() throws Exception {
        enviar("""
                {"nombre": "Ana", "edad": 30, "estado": "confirmado"}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"));
    }

    @Test
    @DisplayName("HU-15 CA3: un campo con el tipo equivocado se rechaza con 400")
    void tipoEquivocado() throws Exception {
        enviar("""
                {"nombre": "Ana", "edad": "treinta"}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"));
        // Un texto con un número adentro tampoco se convierte solo.
        enviar("""
                {"nombre": "Ana", "edad": "30"}
                """)
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Un JSON roto responde 400 VALIDACION, nunca 500")
    void jsonRoto() throws Exception {
        enviar("{\"nombre\": ")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"));
    }

    @Test
    @DisplayName("Un cuerpo correcto pasa")
    void cuerpoCorrecto() throws Exception {
        enviar("""
                {"nombre": "Ana", "edad": 30}
                """)
                .andExpect(status().isOk());
    }

    private ResultActions enviar(String json) throws Exception {
        return mockMvc.perform(post("/prueba/datos").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }
}
