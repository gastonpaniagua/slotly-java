package ar.com.slotly.publico;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

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

    UUID demoId;

    @BeforeEach
    void prepararBase() {
        jdbc.execute("TRUNCATE local, servicio, profesional, cliente, turno, turno_servicio, profesional_servicio, horario");
        demoId = jdbc.queryForObject("""
                INSERT INTO local (nombre, subdominio, color_acento, descripcion, cbu)
                VALUES ('Barbería Demo', 'demo', '#2e7d32', 'Cortes clásicos y modernos', '0000003100000000000001')
                RETURNING id
                """, UUID.class);
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

    @Test
    @DisplayName("HU-01 CA1: devuelve los servicios activos con sus datos, en el orden que definió el local")
    void devuelveLosServiciosEnOrden() throws Exception {
        // Se insertan desordenados a propósito: el orden tiene que salir de la columna orden, no del INSERT.
        crearServicio(demoId, "Barba", 2, true);
        crearServicio(demoId, "Corte de pelo", 1, true);

        mockMvc.perform(get("/api/publico/local").header(HttpHeaders.HOST, "demo.localhost"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.servicios[*].nombre", contains("Corte de pelo", "Barba")))
                .andExpect(jsonPath("$.servicios[0].descripcion").value("Descripción de Corte de pelo"))
                .andExpect(jsonPath("$.servicios[0].duracionMin").value(75))
                .andExpect(jsonPath("$.servicios[0].precio").value(8000))
                .andExpect(jsonPath("$.servicios[0].id").exists());
    }

    @Test
    @DisplayName("HU-01 CA2: un servicio oculto no aparece en la respuesta pública")
    void noDevuelveServiciosOcultos() throws Exception {
        crearServicio(demoId, "Corte de pelo", 1, true);
        crearServicio(demoId, "Color", 2, false);

        mockMvc.perform(get("/api/publico/local").header(HttpHeaders.HOST, "demo.localhost"))
                .andExpect(jsonPath("$.servicios[*].nombre", contains("Corte de pelo")));
    }

    @Test
    @DisplayName("HU-01 CA3: un local sin servicios activos devuelve la lista vacía, no un error")
    void localSinServiciosDevuelveListaVacia() throws Exception {
        mockMvc.perform(get("/api/publico/local").header(HttpHeaders.HOST, "demo.localhost"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.servicios").isArray())
                .andExpect(jsonPath("$.servicios").isEmpty());
    }

    @Test
    @DisplayName("HU-04 CA1: la home de un local no incluye servicios de otro local")
    void noMezclaServiciosDeOtroLocal() throws Exception {
        UUID otraId = jdbc.queryForObject(
                "INSERT INTO local (nombre, subdominio) VALUES ('Otra Barbería', 'otra') RETURNING id", UUID.class);
        crearServicio(demoId, "Corte de pelo", 1, true);
        crearServicio(otraId, "Servicio de otra", 1, true);

        mockMvc.perform(get("/api/publico/local").header(HttpHeaders.HOST, "demo.localhost"))
                .andExpect(jsonPath("$.servicios[*].nombre", contains("Corte de pelo")));
    }

    // Crea un servicio de 75 min y $ 8.000 en ese local.
    private void crearServicio(UUID localId, String nombre, int orden, boolean activo) {
        jdbc.update("""
                INSERT INTO servicio (local_id, nombre, descripcion, duracion_min, precio, orden, activo)
                VALUES (?, ?, ?, 75, 8000, ?, ?)
                """, localId, nombre, "Descripción de " + nombre, orden, activo);
    }
}
