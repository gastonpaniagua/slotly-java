package ar.com.slotly.panel;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import jakarta.servlet.http.Cookie;

import ar.com.slotly.BaseDeTest;
import ar.com.slotly.IntegrationTest;
import ar.com.slotly.seguridad.IntentosDeIngreso;

// HU-17 y HU-18: la agenda del panel, contra la app completa.
// Semana del lunes 16/11/2026. Ana y Bruno trabajan el martes 17 de 10 a 13 y de 16 a 20; Ana también el miércoles.
@IntegrationTest
class AgendaTest {

    private static final String MARTES = "2026-11-17";
    private static final String CLAVE = "clave";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PasswordEncoder encoder;

    @Autowired
    IntentosDeIngreso intentos;

    UUID demoId;
    UUID anaId;
    UUID brunoId;
    UUID carlaId;
    UUID corteId;
    UUID barbaId;
    UUID luciaId;
    Cookie sesionAna;
    Cookie sesionDuenio;

    @BeforeEach
    void preparar() throws Exception {
        intentos.reiniciar();
        BaseDeTest.vaciar(jdbc);
        demoId = crear("INSERT INTO local (nombre, subdominio) VALUES ('Demo', 'demo') RETURNING id");
        UUID otraId = crear("INSERT INTO local (nombre, subdominio) VALUES ('Otra', 'otra') RETURNING id");
        anaId = crear("INSERT INTO profesional (local_id, nombre, orden) VALUES (?, 'Ana', 1) RETURNING id", demoId);
        brunoId = crear("INSERT INTO profesional (local_id, nombre, orden) VALUES (?, 'Bruno', 2) RETURNING id", demoId);
        carlaId = crear("INSERT INTO profesional (local_id, nombre) VALUES (?, 'Carla') RETURNING id", otraId);
        corteId = crear("INSERT INTO servicio (local_id, nombre, duracion_min, precio, orden) VALUES (?, 'Corte', 30, 8000, 1) RETURNING id", demoId);
        barbaId = crear("INSERT INTO servicio (local_id, nombre, duracion_min, precio, orden) VALUES (?, 'Barba', 15, 5000, 2) RETURNING id", demoId);
        luciaId = crear("INSERT INTO cliente (local_id, nombre, telefono) VALUES (?, 'Lucía', '+5491123456789') RETURNING id", demoId);
        for (UUID profesional : new UUID[] { anaId, brunoId }) {
            franja(profesional, 2, "10:00", "13:00");
            franja(profesional, 2, "16:00", "20:00");
        }
        franja(anaId, 3, "10:00", "13:00");

        crearUsuario("ana@demo.com", "profesional", anaId);
        crearUsuario("duenio@demo.com", "admin", null);
        sesionAna = PanelDeTest.ingresar(mockMvc, "demo", "ana@demo.com", CLAVE);
        sesionDuenio = PanelDeTest.ingresar(mockMvc, "demo", "duenio@demo.com", CLAVE);
    }

    @Test
    @DisplayName("HU-17 CA1: el profesional ve sus turnos del día en hora del local, con cliente, servicios y estado, ordenados por hora")
    void agendaDelDia() throws Exception {
        UUID once = turno(anaId, "reserva", "confirmado", MARTES, "11:00", "11:45", luciaId, corteId, barbaId);
        UUID diez = turno(anaId, "manual", "completado", MARTES, "10:00", "10:30", luciaId, corteId);
        turno(brunoId, "reserva", "confirmado", MARTES, "10:00", "10:30", luciaId, corteId);   // de otro: no aparece

        agenda(sesionAna, MARTES, 1, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.desde").value(MARTES))
                .andExpect(jsonPath("$.hasta").value(MARTES))
                .andExpect(jsonPath("$.profesionales[*].nombre", contains("Ana")))
                .andExpect(jsonPath("$.turnos[*].id", contains(diez.toString(), once.toString())))
                .andExpect(jsonPath("$.turnos[1].inicio").value(MARTES + "T11:00"))
                .andExpect(jsonPath("$.turnos[1].fin").value(MARTES + "T11:45"))
                .andExpect(jsonPath("$.turnos[1].estado").value("confirmado"))
                .andExpect(jsonPath("$.turnos[1].clienteNombre").value("Lucía"))
                .andExpect(jsonPath("$.turnos[1].clienteTelefono").value("+5491123456789"))
                .andExpect(jsonPath("$.turnos[1].servicios", contains("Corte", "Barba")))
                .andExpect(jsonPath("$.turnos[0].estado").value("completado"));
    }

    @Test
    @DisplayName("HU-17 CA1: no se muestran los cancelados, los apartados ni los vencidos (ese horario está libre)")
    void soloLoQueOcupa() throws Exception {
        turno(anaId, "reserva", "cancelado", MARTES, "10:00", "10:30", luciaId, corteId);
        turno(anaId, "reserva", "vencido", MARTES, "11:00", "11:30", null, corteId);
        jdbc.update("""
                INSERT INTO turno (local_id, profesional_id, tipo, estado, inicio, fin, ocupado_hasta, vence_en)
                VALUES (?, ?, 'reserva', 'apartado', '2026-11-17T12:00-03:00', '2026-11-17T12:30-03:00',
                        '2026-11-17T12:30-03:00', '2026-11-17T09:05-03:00')
                """, demoId, anaId);
        UUID ausente = turno(anaId, "reserva", "ausente", MARTES, "16:00", "16:30", luciaId, corteId);

        agenda(sesionAna, MARTES, 1, null)
                .andExpect(jsonPath("$.turnos[*].id", contains(ausente.toString())));
    }

    @Test
    @DisplayName("HU-17 CA2: la semana trae los 7 días pedidos y nada de afuera")
    void agendaDeLaSemana() throws Exception {
        UUID martes = turno(anaId, "reserva", "confirmado", MARTES, "10:00", "10:30", luciaId, corteId);
        UUID miercoles = turno(anaId, "reserva", "confirmado", "2026-11-18", "10:00", "10:30", luciaId, corteId);
        UUID domingo = turno(anaId, "manual", "confirmado", "2026-11-22", "23:30", "23:59", luciaId, corteId);
        turno(anaId, "reserva", "confirmado", "2026-11-23", "10:00", "10:30", luciaId, corteId);   // lunes siguiente
        turno(anaId, "manual", "confirmado", "2026-11-15", "23:00", "23:30", luciaId, corteId);    // domingo anterior

        agenda(sesionAna, "2026-11-16", 7, null)
                .andExpect(jsonPath("$.desde").value("2026-11-16"))
                .andExpect(jsonPath("$.hasta").value("2026-11-22"))
                .andExpect(jsonPath("$.turnos[*].id", contains(martes.toString(), miercoles.toString(), domingo.toString())))
                .andExpect(jsonPath("$.franjas", hasSize(3)));
    }

    @Test
    @DisplayName("HU-17 CA3: un profesional que pide la agenda de otro (o la de todos) recibe 403")
    void agendaDeOtro() throws Exception {
        agenda(sesionAna, MARTES, 1, brunoId.toString())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("SIN_PERMISO"));
        agenda(sesionAna, MARTES, 1, "todos").andExpect(status().isForbidden());
        agenda(sesionAna, MARTES, 1, anaId.toString()).andExpect(status().isOk());
    }

    @Test
    @DisplayName("HU-17 CA4: un día sin turnos responde bien, con la lista vacía")
    void diaSinTurnos() throws Exception {
        agenda(sesionAna, "2026-11-19", 1, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.turnos", hasSize(0)))
                .andExpect(jsonPath("$.franjas", hasSize(0)));
    }

    @Test
    @DisplayName("HU-17 CA5: los bloqueos vienen marcados como bloqueo con su motivo, y el horario de trabajo muestra la pausa")
    void bloqueosYPausas() throws Exception {
        UUID bloqueo = turno(anaId, "bloqueo", "confirmado", MARTES, "16:00", "17:00", null);
        jdbc.update("UPDATE turno SET motivo = 'Médico' WHERE id = ?", bloqueo);
        // Unas vacaciones que empezaron el sábado y terminan el martes a las 9 también aparecen ese martes.
        UUID vacaciones = turno(anaId, "bloqueo", "confirmado", "2026-11-14", "00:00", "23:59", null);
        jdbc.update("UPDATE turno SET fin = '2026-11-17T09:00-03:00', ocupado_hasta = '2026-11-17T09:00-03:00' WHERE id = ?", vacaciones);

        agenda(sesionAna, MARTES, 1, null)
                .andExpect(jsonPath("$.turnos[*].id", contains(vacaciones.toString(), bloqueo.toString())))
                .andExpect(jsonPath("$.turnos[1].tipo").value("bloqueo"))
                .andExpect(jsonPath("$.turnos[1].motivo").value("Médico"))
                .andExpect(jsonPath("$.turnos[1].clienteNombre").value(nullValue()))
                .andExpect(jsonPath("$.franjas[*].desde", contains("10:00", "16:00")))
                .andExpect(jsonPath("$.franjas[*].hasta", contains("13:00", "20:00")));
    }

    @Test
    @DisplayName("HU-18 CA1: el admin ve a todos juntos y puede filtrar por uno")
    void adminVeATodos() throws Exception {
        UUID deAna = turno(anaId, "reserva", "confirmado", MARTES, "11:00", "11:30", luciaId, corteId);
        UUID deBruno = turno(brunoId, "reserva", "confirmado", MARTES, "10:00", "10:30", luciaId, corteId);

        agenda(sesionDuenio, MARTES, 1, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profesionales[*].nombre", contains("Ana", "Bruno")))
                .andExpect(jsonPath("$.turnos[*].id", contains(deBruno.toString(), deAna.toString())))
                .andExpect(jsonPath("$.franjas", hasSize(4)));
        agenda(sesionDuenio, MARTES, 1, "todos")
                .andExpect(jsonPath("$.turnos", hasSize(2)));
        agenda(sesionDuenio, MARTES, 1, brunoId.toString())
                .andExpect(jsonPath("$.profesionales[*].nombre", contains("Bruno")))
                .andExpect(jsonPath("$.turnos[*].id", contains(deBruno.toString())));
    }

    @Test
    @DisplayName("HU-18 CA2: un profesional de otro local (o un id inventado) da 404 al admin")
    void profesionalDeOtroLocal() throws Exception {
        agenda(sesionDuenio, MARTES, 1, carlaId.toString())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("PROFESIONAL_NO_ENCONTRADO"));
        agenda(sesionDuenio, MARTES, 1, UUID.randomUUID().toString()).andExpect(status().isNotFound());
        agenda(sesionDuenio, MARTES, 1, "cualquier-cosa").andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("la agenda pide sesión, y se pide de 1 o de 7 días")
    void validaciones() throws Exception {
        mockMvc.perform(get("/api/panel/agenda").header(HttpHeaders.HOST, "demo.localhost")
                        .param("desde", MARTES).param("dias", "1"))
                .andExpect(status().isUnauthorized());
        agenda(sesionAna, MARTES, 3, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"));
        mockMvc.perform(get("/api/panel/agenda").header(HttpHeaders.HOST, "demo.localhost").cookie(sesionAna)
                        .param("desde", "17/11/2026").param("dias", "1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"));
    }

    private ResultActions agenda(Cookie sesion, String desde, int dias, String profesional) throws Exception {
        MockHttpServletRequestBuilder pedido = get("/api/panel/agenda")
                .header(HttpHeaders.HOST, "demo.localhost")
                .cookie(sesion)
                .param("desde", desde)
                .param("dias", String.valueOf(dias));
        if (profesional != null) {
            pedido.param("profesional", profesional);
        }
        return mockMvc.perform(pedido);
    }

    // Un turno del local demo, en hora de Buenos Aires, con sus servicios.
    private UUID turno(UUID profesional, String tipo, String estado, String fecha, String desde, String hasta,
            UUID cliente, UUID... servicios) {
        String inicio = fecha + "T" + desde + "-03:00";
        String fin = fecha + "T" + hasta + "-03:00";
        UUID id = jdbc.queryForObject("""
                INSERT INTO turno (local_id, profesional_id, cliente_id, tipo, estado, inicio, fin, ocupado_hasta)
                VALUES (?, ?, ?, ?, ?, ?::timestamptz, ?::timestamptz, ?::timestamptz) RETURNING id
                """, UUID.class, demoId, profesional, cliente, tipo, estado, inicio, fin, fin);
        for (UUID servicio : servicios) {
            jdbc.update("INSERT INTO turno_servicio (local_id, turno_id, servicio_id, duracion_min, precio) VALUES (?, ?, ?, 30, 8000)",
                    demoId, id, servicio);
        }
        return id;
    }

    private void franja(UUID profesional, int diaSemana, String desde, String hasta) {
        jdbc.update("INSERT INTO horario (local_id, profesional_id, dia_semana, hora_inicio, hora_fin) VALUES (?, ?, ?, ?::time, ?::time)",
                demoId, profesional, diaSemana, desde, hasta);
    }

    private void crearUsuario(String email, String rol, UUID profesional) {
        jdbc.update("INSERT INTO usuario (local_id, email, password_hash, rol, profesional_id) VALUES (?, ?, ?, ?, ?)",
                demoId, email, encoder.encode(CLAVE), rol, profesional);
    }

    private UUID crear(String sql, Object... parametros) {
        return jdbc.queryForObject(sql, UUID.class, parametros);
    }
}
