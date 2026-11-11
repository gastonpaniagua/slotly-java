package ar.com.slotly.publico;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import ar.com.slotly.IntegrationTest;

// GET /api/publico/local de punta a punta: header Host → local en Postgres real → JSON.
@IntegrationTest
class PublicoControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void prepararBase() {
        jdbc.execute("TRUNCATE local, servicio, profesional, cliente, turno, turno_servicio, profesional_servicio, horario");
        jdbc.update("""
                INSERT INTO local (nombre, subdominio, color_acento, descripcion, cbu)
                VALUES ('Barbería Demo', 'demo', '#2e7d32', 'Cortes clásicos y modernos', '0000003100000000000001')
                """);
    }

    @Test
    @DisplayName("HU-03 CA1: con el Host del local devuelve su nombre y su marca, sin datos internos")
    void devuelveLosDatosDelLocal() throws Exception {
        mockMvc.perform(get("/api/publico/local").header(HttpHeaders.HOST, "demo.localhost:5173"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Barbería Demo"))
                .andExpect(jsonPath("$.colorAcento").value("#2e7d32"))
                .andExpect(jsonPath("$.descripcion").value("Cortes clásicos y modernos"))
                .andExpect(jsonPath("$.cbu").doesNotExist())
                .andExpect(jsonPath("$.activo").doesNotExist());
    }

    @Test
    @DisplayName("HU-03 CA3: un Host en mayúsculas se normaliza y encuentra el local")
    void hostEnMayusculasSeNormaliza() throws Exception {
        mockMvc.perform(get("/api/publico/local").header(HttpHeaders.HOST, "DEMO.localhost:5173"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Barbería Demo"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"noexiste.localhost:5173", "localhost:5173", "de mo.localhost", "../", "a.b.localhost"})
    @DisplayName("HU-03 CA2 y CA3: un Host sin local válido responde 404 LOCAL_NO_ENCONTRADO, nunca 500")
    void hostSinLocalResponde404(String host) throws Exception {
        mockMvc.perform(get("/api/publico/local").header(HttpHeaders.HOST, host))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("LOCAL_NO_ENCONTRADO"));
    }
}
