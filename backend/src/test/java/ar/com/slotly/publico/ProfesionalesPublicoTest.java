package ar.com.slotly.publico;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import ar.com.slotly.IntegrationTest;

// GET /api/publico/profesionales: quién puede hacer los servicios elegidos (paso 2 de la reserva), contra Postgres real.
@IntegrationTest
class ProfesionalesPublicoTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    UUID demoId;
    UUID corteId;
    UUID barbaId;

    @BeforeEach
    void prepararBase() {
        jdbc.execute("TRUNCATE local, servicio, profesional, cliente, turno, turno_servicio, profesional_servicio, horario");
        demoId = crearLocal("demo");
        corteId = crearServicio(demoId, "Corte", true);
        barbaId = crearServicio(demoId, "Barba", true);
    }

    @Test
    @DisplayName("HU-06 CA1: devuelve solo los profesionales activos que hacen todos los servicios elegidos, en su orden")
    void devuelveLosQueHacenTodos() throws Exception {
        UUID bruno = crearProfesional(demoId, "Bruno", 2, true);
        UUID ana = crearProfesional(demoId, "Ana", 1, true);
        UUID carla = crearProfesional(demoId, "Carla", 3, false);  // hace todo, pero está inactiva
        UUID dario = crearProfesional(demoId, "Darío", 4, true);   // solo hace corte
        hace(ana, corteId, barbaId);
        hace(bruno, corteId, barbaId);
        hace(carla, corteId, barbaId);
        hace(dario, corteId);

        pedir(corteId + "," + barbaId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].nombre", contains("Ana", "Bruno")))
                .andExpect(jsonPath("$[0].id").value(ana.toString()));

        pedir(corteId.toString())
                .andExpect(jsonPath("$[*].nombre", contains("Ana", "Bruno", "Darío")));
    }

    @Test
    @DisplayName("HU-06 CA2: si nadie hace todos los servicios elegidos, devuelve la lista vacía (no un error)")
    void nadieHaceLaCombinacion() throws Exception {
        UUID ana = crearProfesional(demoId, "Ana", 1, true);
        hace(ana, corteId);

        pedir(corteId + "," + barbaId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    @DisplayName("HU-06 CA4 y HU-04 CA3: un servicio de otro local responde 404 SERVICIO_NO_ENCONTRADO")
    void servicioDeOtroLocalDa404() throws Exception {
        UUID otraId = crearLocal("otra");
        UUID servicioDeOtra = crearServicio(otraId, "Corte de otra", true);

        pedir(servicioDeOtra.toString())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("SERVICIO_NO_ENCONTRADO"));

        // Mezclado con uno propio, también: no alcanza con que alguno sea del local.
        pedir(corteId + "," + servicioDeOtra)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("SERVICIO_NO_ENCONTRADO"));
    }

    @Test
    @DisplayName("HU-06 CA4: un servicio oculto o inexistente responde el mismo 404")
    void servicioOcultoOInexistenteDa404() throws Exception {
        UUID oculto = crearServicio(demoId, "Color", false);

        pedir(oculto.toString()).andExpect(status().isNotFound());
        pedir(UUID.randomUUID().toString()).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("HU-06: sin servicios o con un id mal formado responde 400, nunca 500")
    void pedidoInvalidoDa400() throws Exception {
        pedir("")
                .andExpect(status().isBadRequest());
        pedir("no-es-un-uuid")
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/publico/profesionales").header(HttpHeaders.HOST, "demo.localhost"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("HU-06: el mismo servicio repetido cuenta una sola vez")
    void servicioRepetidoCuentaUnaVez() throws Exception {
        UUID ana = crearProfesional(demoId, "Ana", 1, true);
        hace(ana, corteId);

        pedir(corteId + "," + corteId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].nombre", contains("Ana")));
    }

    private ResultActions pedir(String servicios) throws Exception {
        return mockMvc.perform(get("/api/publico/profesionales")
                .header(HttpHeaders.HOST, "demo.localhost")
                .param("servicios", servicios));
    }

    private UUID crearLocal(String subdominio) {
        return jdbc.queryForObject("INSERT INTO local (nombre, subdominio) VALUES (?, ?) RETURNING id",
                UUID.class, "Local " + subdominio, subdominio);
    }

    private UUID crearServicio(UUID localId, String nombre, boolean activo) {
        return jdbc.queryForObject("""
                INSERT INTO servicio (local_id, nombre, duracion_min, precio, activo)
                VALUES (?, ?, 30, 8000, ?) RETURNING id
                """, UUID.class, localId, nombre, activo);
    }

    private UUID crearProfesional(UUID localId, String nombre, int orden, boolean activo) {
        return jdbc.queryForObject("""
                INSERT INTO profesional (local_id, nombre, orden, activo) VALUES (?, ?, ?, ?) RETURNING id
                """, UUID.class, localId, nombre, orden, activo);
    }

    // Registra que el profesional hace esos servicios (todos del local demo).
    private void hace(UUID profesionalId, UUID... servicios) {
        for (UUID servicioId : servicios) {
            jdbc.update("INSERT INTO profesional_servicio (local_id, profesional_id, servicio_id) VALUES (?, ?, ?)",
                    demoId, profesionalId, servicioId);
        }
    }
}
