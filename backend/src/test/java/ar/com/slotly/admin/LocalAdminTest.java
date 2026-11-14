package ar.com.slotly.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import jakarta.servlet.http.Cookie;

import ar.com.slotly.BaseDeTest;
import ar.com.slotly.IntegrationTest;
import ar.com.slotly.RelojDeTest;
import ar.com.slotly.panel.PanelDeTest;
import ar.com.slotly.seguridad.IntentosDeIngreso;

// HU-32: configurar datos, imagen y reglas del local, contra la app completa.
// Reloj: lunes 16/11/2026 a las 9:00 en Buenos Aires. Ana hace Corte (30 min) y trabaja el martes de 10 a 13.
@IntegrationTest
class LocalAdminTest {

    private static final String CLAVE = "clave";

    // Una configuración completa y válida; cada test cambia lo que le importa.
    private static final String BASE = """
            {"nombre": "Barbería Nueva", "descripcion": "Cortes y barba.", "logoUrl": null, "colorAcento": "#C0392B",
             "whatsapp": "353 400-0000", "instagram": "@barberia.nueva", "tiktok": "", "facebook": null,
             "direccion": "San Martín 1200", "ciudad": "Villa María", "alias": "barberia.nueva", "titularCuenta": "Juan Pérez",
             "cbu": "0000003100000000000001", "linkResena": "https://g.page/r/abc/review",
             "intervaloMin": 15, "limpiezaMin": 0, "anticipacionMin": 60, "topeDias": 30, "cancelarHastaHoras": 3,
             "maxTurnosFuturos": 2}
            """;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PasswordEncoder encoder;

    @Autowired
    IntentosDeIngreso intentos;

    @Autowired
    RelojDeTest reloj;

    UUID demoId;
    UUID anaId;
    UUID corteId;
    Cookie admin;

    @BeforeEach
    void preparar() throws Exception {
        reloj.fijar(Instant.parse("2026-11-16T12:00:00Z"));
        intentos.reiniciar();
        BaseDeTest.vaciar(jdbc);
        demoId = jdbc.queryForObject("INSERT INTO local (nombre, subdominio) VALUES ('Demo', 'demo') RETURNING id", UUID.class);
        corteId = jdbc.queryForObject(
                "INSERT INTO servicio (local_id, nombre, duracion_min, precio) VALUES (?, 'Corte', 30, 8000) RETURNING id",
                UUID.class, demoId);
        anaId = jdbc.queryForObject("INSERT INTO profesional (local_id, nombre) VALUES (?, 'Ana') RETURNING id", UUID.class, demoId);
        jdbc.update("INSERT INTO profesional_servicio (local_id, profesional_id, servicio_id) VALUES (?, ?, ?)", demoId, anaId, corteId);
        jdbc.update("INSERT INTO horario (local_id, profesional_id, dia_semana, hora_inicio, hora_fin) VALUES (?, ?, 2, '10:00', '13:00')",
                demoId, anaId);
        jdbc.update("INSERT INTO usuario (local_id, email, password_hash, rol) VALUES (?, 'duenio@demo.com', ?, 'admin')",
                demoId, encoder.encode(CLAVE));
        admin = PanelDeTest.ingresar(mockMvc, "demo", "duenio@demo.com", CLAVE);
    }

    @Test
    @DisplayName("HU-32 CA1: los datos guardados se ven en la web pública (color en minúsculas, redes sin @, WhatsApp normalizado)")
    void datosEnLaWebPublica() throws Exception {
        guardar(BASE)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.colorAcento").value("#c0392b"))
                .andExpect(jsonPath("$.whatsapp").value("+5493534000000"))
                .andExpect(jsonPath("$.instagram").value("barberia.nueva"))
                .andExpect(jsonPath("$.tiktok").doesNotExist())
                .andExpect(jsonPath("$.linkResena").value("https://g.page/r/abc/review"));

        mockMvc.perform(get("/api/publico/local").header(HttpHeaders.HOST, "demo.localhost"))
                .andExpect(jsonPath("$.nombre").value("Barbería Nueva"))
                .andExpect(jsonPath("$.colorAcento").value("#c0392b"))
                .andExpect(jsonPath("$.whatsapp").value("+5493534000000"))
                .andExpect(jsonPath("$.instagram").value("barberia.nueva"))
                .andExpect(jsonPath("$.direccion").value("San Martín 1200"));
        assertThat(jdbc.queryForObject("SELECT alias FROM local WHERE id = ?", String.class, demoId)).isEqualTo("barberia.nueva");
    }

    @Test
    @DisplayName("HU-32 CA2: un intervalo de 30 minutos cambia las horas que ofrece la reserva")
    void reglasEnLaDisponibilidad() throws Exception {
        guardar(BASE.replace("\"intervaloMin\": 15", "\"intervaloMin\": 30")).andExpect(status().isOk());

        mockMvc.perform(get("/api/publico/disponibilidad/horas").header(HttpHeaders.HOST, "demo.localhost")
                        .param("servicios", corteId.toString()).param("profesional", anaId.toString()).param("fecha", "2026-11-17"))
                .andExpect(jsonPath("$.horas", hasItem("10:30")))
                .andExpect(jsonPath("$.horas", everyItem(matchesPattern("\\d\\d:(00|30)"))));
    }

    @Test
    @DisplayName("HU-32 CA3: valores fuera de rango se rechazan con el mensaje de cada campo, y no se guarda nada")
    void fueraDeRango() throws Exception {
        guardar(BASE.replace("\"intervaloMin\": 15", "\"intervaloMin\": 61")
                        .replace("\"limpiezaMin\": 0", "\"limpiezaMin\": 31")
                        .replace("\"cbu\": \"0000003100000000000001\"", "\"cbu\": \"123\"")
                        .replace("\"colorAcento\": \"#C0392B\"", "\"colorAcento\": \"rojo\""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[*].campo", containsInAnyOrder("intervaloMin", "limpiezaMin", "cbu", "colorAcento")));

        assertThat(jdbc.queryForObject("SELECT nombre FROM local WHERE id = ?", String.class, demoId)).isEqualTo("Demo");
    }

    @Test
    @DisplayName("HU-32: un profesional no puede configurar el local (403)")
    void profesionalNo() throws Exception {
        jdbc.update("INSERT INTO usuario (local_id, email, password_hash, rol, profesional_id) VALUES (?, 'ana@demo.com', ?, 'profesional', ?)",
                demoId, encoder.encode(CLAVE), anaId);
        Cookie ana = PanelDeTest.ingresar(mockMvc, "demo", "ana@demo.com", CLAVE);

        mockMvc.perform(get("/api/admin/local").header(HttpHeaders.HOST, "demo.localhost").cookie(ana))
                .andExpect(status().isForbidden());
    }

    private ResultActions guardar(String cuerpo) throws Exception {
        return mockMvc.perform(put("/api/admin/local").header(HttpHeaders.HOST, "demo.localhost").cookie(admin)
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo).with(PanelDeTest.conCsrf()));
    }
}
