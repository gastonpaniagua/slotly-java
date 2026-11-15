package ar.com.slotly.admin;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import jakarta.servlet.http.Cookie;

import ar.com.slotly.BaseDeTest;
import ar.com.slotly.IntegrationTest;
import ar.com.slotly.RelojDeTest;
import ar.com.slotly.panel.PanelDeTest;
import ar.com.slotly.seguridad.IntentosDeIngreso;

// HU-33: el resumen de la semana, contra la app completa. La semana va del lunes 16 al domingo 22 de noviembre.
@IntegrationTest
class ResumenAdminTest {

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
    UUID clienteId;
    UUID corteId;
    Cookie admin;

    @BeforeEach
    void preparar() throws Exception {
        // Martes 17 a las 22:30 en Buenos Aires = miércoles 18 a la 1:30 en UTC: el "hoy" tiene que ser el del local.
        reloj.fijar(Instant.parse("2026-11-18T01:30:00Z"));
        intentos.reiniciar();
        BaseDeTest.vaciar(jdbc);
        demoId = jdbc.queryForObject("INSERT INTO local (nombre, subdominio) VALUES ('Demo', 'demo') RETURNING id", UUID.class);
        corteId = jdbc.queryForObject(
                "INSERT INTO servicio (local_id, nombre, duracion_min, precio) VALUES (?, 'Corte', 30, 8000) RETURNING id",
                UUID.class, demoId);
        anaId = jdbc.queryForObject("INSERT INTO profesional (local_id, nombre) VALUES (?, 'Ana') RETURNING id", UUID.class, demoId);
        clienteId = jdbc.queryForObject("INSERT INTO cliente (local_id, nombre) VALUES (?, 'Lucía') RETURNING id", UUID.class, demoId);
        jdbc.update("INSERT INTO usuario (local_id, email, password_hash, rol) VALUES (?, 'duenio@demo.com', ?, 'admin')",
                demoId, encoder.encode(CLAVE));
        admin = PanelDeTest.ingresar(mockMvc, "demo", "duenio@demo.com", CLAVE);
    }

    @Test
    @DisplayName("HU-33 CA1: turnos de hoy y de la semana, suma de precios de los no cancelados y cantidad de 'no vino'")
    void resumen() throws Exception {
        turno("2026-11-17T10:00", "confirmado", 8000);    // hoy
        turno("2026-11-17T11:00", "completado", 13000);   // hoy
        turno("2026-11-17T12:00", "cancelado", 8000);     // hoy, pero cancelado: no cuenta
        turno("2026-11-16T10:00", "ausente", 8000);       // lunes: falta (y su precio estimado cuenta)
        turno("2026-11-22T19:00", "confirmado", 5000);    // domingo, último día de la semana
        turno("2026-11-23T10:00", "confirmado", 8000);    // lunes siguiente: fuera
        turno("2026-11-15T10:00", "completado", 8000);    // domingo anterior: fuera
        jdbc.update("""
                INSERT INTO turno (local_id, profesional_id, tipo, estado, inicio, fin, ocupado_hasta)
                VALUES (?, ?, 'bloqueo', 'confirmado', '2026-11-18T10:00-03:00', '2026-11-18T13:00-03:00', '2026-11-18T13:00-03:00')
                """, demoId, anaId);   // un bloqueo no es un turno

        mockMvc.perform(get("/api/admin/resumen").header(HttpHeaders.HOST, "demo.localhost").cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hoy").value("2026-11-17"))
                .andExpect(jsonPath("$.semanaDesde").value("2026-11-16"))
                .andExpect(jsonPath("$.semanaHasta").value("2026-11-22"))
                .andExpect(jsonPath("$.turnosHoy").value(2))
                .andExpect(jsonPath("$.turnosSemana").value(4))
                .andExpect(jsonPath("$.ingresosSemana").value(8000 + 13000 + 8000 + 5000))
                .andExpect(jsonPath("$.faltasSemana").value(1));
    }

    @Test
    @DisplayName("HU-33: una semana sin turnos da todo en cero (no un error)")
    void semanaVacia() throws Exception {
        mockMvc.perform(get("/api/admin/resumen").header(HttpHeaders.HOST, "demo.localhost").cookie(admin))
                .andExpect(jsonPath("$.turnosSemana").value(0))
                .andExpect(jsonPath("$.ingresosSemana").value(0));
    }

    @Test
    @DisplayName("HU-33: un profesional no ve el resumen (403)")
    void profesionalNo() throws Exception {
        jdbc.update("INSERT INTO usuario (local_id, email, password_hash, rol, profesional_id) VALUES (?, 'ana@demo.com', ?, 'profesional', ?)",
                demoId, encoder.encode(CLAVE), anaId);
        Cookie ana = PanelDeTest.ingresar(mockMvc, "demo", "ana@demo.com", CLAVE);

        mockMvc.perform(get("/api/admin/resumen").header(HttpHeaders.HOST, "demo.localhost").cookie(ana))
                .andExpect(status().isForbidden());
    }

    private void turno(String inicio, String estado, int precio) {
        UUID id = jdbc.queryForObject("""
                INSERT INTO turno (local_id, profesional_id, cliente_id, tipo, estado, inicio, fin, ocupado_hasta)
                VALUES (?, ?, ?, 'manual', ?, ?::timestamptz, ?::timestamptz + interval '30 minutes', ?::timestamptz + interval '30 minutes')
                RETURNING id
                """, UUID.class, demoId, anaId, clienteId, estado, inicio + "-03:00", inicio + "-03:00", inicio + "-03:00");
        jdbc.update("INSERT INTO turno_servicio (local_id, turno_id, servicio_id, duracion_min, precio) VALUES (?, ?, ?, 30, ?)",
                demoId, id, corteId, precio);
    }
}
