package ar.com.slotly.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.hasItem;
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

// HU-26: el horario semanal de un profesional, contra la app completa.
// Reloj: lunes 16/11/2026 a las 9:00 en Buenos Aires. Ana hace Corte (30 min); se mira el martes 17.
@IntegrationTest
class HorarioAdminTest {

    private static final String CLAVE = "clave";

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
        jdbc.update("INSERT INTO usuario (local_id, email, password_hash, rol) VALUES (?, 'duenio@demo.com', ?, 'admin')",
                demoId, encoder.encode(CLAVE));
        admin = PanelDeTest.ingresar(mockMvc, "demo", "duenio@demo.com", CLAVE);
    }

    @Test
    @DisplayName("HU-26 CA1: con dos franjas el martes (10 a 13 y 16 a 20), la reserva ofrece horas solo dentro de ellas")
    void dosFranjas() throws Exception {
        guardar("""
                [{"diaSemana": 2, "desde": "16:00", "hasta": "20:00"}, {"diaSemana": 2, "desde": "10:00", "hasta": "13:00"}]
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.franjas[*].desde", contains("10:00", "16:00")))
                .andExpect(jsonPath("$.turnosAfuera", hasSize(0)));

        horasDelMartes()
                .andExpect(jsonPath("$.horas", hasItems("10:00", "12:30", "16:00", "19:30")))
                .andExpect(jsonPath("$.horas", not(hasItem("12:45"))))
                .andExpect(jsonPath("$.horas", not(hasItem("14:00"))));
        mockMvc.perform(get("/api/admin/profesionales/" + anaId + "/horario").header(HttpHeaders.HOST, "demo.localhost").cookie(admin))
                .andExpect(jsonPath("$[*].hasta", contains("13:00", "20:00")));
    }

    @Test
    @DisplayName("HU-26 CA2: dos franjas que se pisan el mismo día se rechazan con un mensaje claro, y no se guarda nada")
    void franjasQueSePisan() throws Exception {
        guardar("[{\"diaSemana\": 2, \"desde\": \"10:00\", \"hasta\": \"13:00\"}]");

        guardar("""
                [{"diaSemana": 2, "desde": "10:00", "hasta": "13:00"}, {"diaSemana": 2, "desde": "12:00", "hasta": "14:00"}]
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("franjas"))
                .andExpect(jsonPath("$.errores[0].mensaje")
                        .value("El martes tiene dos franjas que se pisan: 10:00 a 13:00 y 12:00 a 14:00."));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM horario", Integer.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("HU-26 CA2: una franja que termina antes de empezar se rechaza; dos pegadas (13 y 13) sí valen")
    void franjaAlReves() throws Exception {
        guardar("[{\"diaSemana\": 3, \"desde\": \"13:00\", \"hasta\": \"10:00\"}]")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].mensaje").value("La franja del miércoles de 13:00 a 10:00 termina antes de empezar."));

        guardar("""
                [{"diaSemana": 3, "desde": "10:00", "hasta": "13:00"}, {"diaSemana": 3, "desde": "13:00", "hasta": "16:00"}]
                """)
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("HU-26 CA3: si el horario nuevo deja afuera turnos ya reservados, los avisa y no los cancela")
    void turnosQueQuedanAfuera() throws Exception {
        guardar("[{\"diaSemana\": 2, \"desde\": \"10:00\", \"hasta\": \"20:00\"}]");
        UUID cliente = jdbc.queryForObject("INSERT INTO cliente (local_id, nombre) VALUES (?, 'Lucía') RETURNING id", UUID.class, demoId);
        UUID afuera = turno(cliente, "17:00", "17:30");
        turno(cliente, "11:00", "11:30");   // queda adentro

        guardar("[{\"diaSemana\": 2, \"desde\": \"10:00\", \"hasta\": \"13:00\"}]")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.turnosAfuera", hasSize(1)))
                .andExpect(jsonPath("$.turnosAfuera[0].id").value(afuera.toString()))
                .andExpect(jsonPath("$.turnosAfuera[0].inicio").value("2026-11-17T17:00"))
                .andExpect(jsonPath("$.turnosAfuera[0].clienteNombre").value("Lucía"));
        assertThat(jdbc.queryForObject("SELECT estado FROM turno WHERE id = ?", String.class, afuera)).isEqualTo("confirmado");
    }

    @Test
    @DisplayName("HU-26: un profesional de otro local da 404, y un profesional no puede cargar horarios (403)")
    void permisos() throws Exception {
        UUID otraId = jdbc.queryForObject("INSERT INTO local (nombre, subdominio) VALUES ('Otra', 'otra') RETURNING id", UUID.class);
        UUID ajeno = jdbc.queryForObject("INSERT INTO profesional (local_id, nombre) VALUES (?, 'Ajeno') RETURNING id", UUID.class, otraId);
        mockMvc.perform(put("/api/admin/profesionales/" + ajeno + "/horario").header(HttpHeaders.HOST, "demo.localhost")
                        .cookie(admin).contentType(MediaType.APPLICATION_JSON).content("{\"franjas\": []}").with(PanelDeTest.conCsrf()))
                .andExpect(status().isNotFound());

        jdbc.update("INSERT INTO usuario (local_id, email, password_hash, rol, profesional_id) VALUES (?, 'ana@demo.com', ?, 'profesional', ?)",
                demoId, encoder.encode(CLAVE), anaId);
        Cookie ana = PanelDeTest.ingresar(mockMvc, "demo", "ana@demo.com", CLAVE);
        mockMvc.perform(put("/api/admin/profesionales/" + anaId + "/horario").header(HttpHeaders.HOST, "demo.localhost")
                        .cookie(ana).contentType(MediaType.APPLICATION_JSON).content("{\"franjas\": []}").with(PanelDeTest.conCsrf()))
                .andExpect(status().isForbidden());
    }

    private ResultActions guardar(String franjas) throws Exception {
        return mockMvc.perform(put("/api/admin/profesionales/" + anaId + "/horario").header(HttpHeaders.HOST, "demo.localhost")
                .cookie(admin).contentType(MediaType.APPLICATION_JSON).content("{\"franjas\": " + franjas + "}")
                .with(PanelDeTest.conCsrf()));
    }

    private ResultActions horasDelMartes() throws Exception {
        return mockMvc.perform(get("/api/publico/disponibilidad/horas").header(HttpHeaders.HOST, "demo.localhost")
                .param("servicios", corteId.toString()).param("profesional", anaId.toString()).param("fecha", "2026-11-17"));
    }

    private UUID turno(UUID cliente, String desde, String hasta) {
        return jdbc.queryForObject("""
                INSERT INTO turno (local_id, profesional_id, cliente_id, tipo, estado, inicio, fin, ocupado_hasta)
                VALUES (?, ?, ?, 'manual', 'confirmado', ?::timestamptz, ?::timestamptz, ?::timestamptz) RETURNING id
                """, UUID.class, demoId, anaId, cliente, "2026-11-17T" + desde + "-03:00", "2026-11-17T" + hasta + "-03:00",
                "2026-11-17T" + hasta + "-03:00");
    }
}
