package ar.com.slotly.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

// HU-30 (vino / no vino), HU-31 (clientes) y HU-34 (borrar datos), contra la app completa.
// Reloj: martes 17/11/2026 a las 11:00 en Buenos Aires. Lucía tiene un turno con Ana el martes a las 10:00.
@IntegrationTest
class ClientesYAsistenciaTest {

    private static final String CLAVE = "clave";
    private static final Instant MARTES_11 = Instant.parse("2026-11-17T14:00:00Z");

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
    UUID brunoId;
    UUID corteId;
    UUID luciaId;
    Cookie admin;
    Cookie ana;

    @BeforeEach
    void preparar() throws Exception {
        reloj.fijar(MARTES_11);
        intentos.reiniciar();
        BaseDeTest.vaciar(jdbc);
        demoId = jdbc.queryForObject("INSERT INTO local (nombre, subdominio) VALUES ('Demo', 'demo') RETURNING id", UUID.class);
        corteId = jdbc.queryForObject(
                "INSERT INTO servicio (local_id, nombre, duracion_min, precio) VALUES (?, 'Corte', 30, 8000) RETURNING id",
                UUID.class, demoId);
        anaId = jdbc.queryForObject("INSERT INTO profesional (local_id, nombre) VALUES (?, 'Ana') RETURNING id", UUID.class, demoId);
        brunoId = jdbc.queryForObject("INSERT INTO profesional (local_id, nombre) VALUES (?, 'Bruno') RETURNING id", UUID.class, demoId);
        luciaId = cliente("Lucía", "+5491123456789");
        jdbc.update("INSERT INTO usuario (local_id, email, password_hash, rol) VALUES (?, 'duenio@demo.com', ?, 'admin')",
                demoId, encoder.encode(CLAVE));
        jdbc.update("INSERT INTO usuario (local_id, email, password_hash, rol, profesional_id) VALUES (?, 'ana@demo.com', ?, 'profesional', ?)",
                demoId, encoder.encode(CLAVE), anaId);
        admin = PanelDeTest.ingresar(mockMvc, "demo", "duenio@demo.com", CLAVE);
        ana = PanelDeTest.ingresar(mockMvc, "demo", "ana@demo.com", CLAVE);
    }

    @Test
    @DisplayName("HU-30 CA1: marcar 'vino' o 'no vino' un turno que ya empezó cambia su estado y el contador del cliente")
    void marcarAsistencia() throws Exception {
        UUID vino = turno(anaId, luciaId, "2026-11-17T10:00", "confirmado");
        UUID noVino = turno(anaId, luciaId, "2026-11-10T10:00", "confirmado");

        asistencia(ana, vino, true).andExpect(status().isOk());
        asistencia(ana, noVino, false).andExpect(status().isOk());

        assertThat(estado(vino)).isEqualTo("completado");
        assertThat(estado(noVino)).isEqualTo("ausente");
        clientes(admin, null)
                .andExpect(jsonPath("$[0].visitas").value(1))
                .andExpect(jsonPath("$[0].faltas").value(1))
                .andExpect(jsonPath("$[0].ultimaVisita").value("2026-11-17"))
                .andExpect(jsonPath("$[0].datosBorrados").value(false));
    }

    @Test
    @DisplayName("HU-30 CA2: un turno que todavía no empezó no se puede marcar")
    void todaviaNoEmpezo() throws Exception {
        UUID turno = turno(anaId, luciaId, "2026-11-17T12:00", "confirmado");

        asistencia(ana, turno, true)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("El turno todavía no empezó."));
        assertThat(estado(turno)).isEqualTo("confirmado");
        mockMvc.perform(get("/api/panel/agenda").header(HttpHeaders.HOST, "demo.localhost").cookie(ana)
                        .param("desde", "2026-11-17").param("dias", "1"))
                .andExpect(jsonPath("$.turnos[0].yaEmpezo").value(false));
    }

    @Test
    @DisplayName("HU-30 CA3: una marca equivocada se corrige el mismo día; al día siguiente ya no")
    void corregirElMismoDia() throws Exception {
        UUID turno = turno(anaId, luciaId, "2026-11-17T10:00", "confirmado");
        asistencia(ana, turno, false);

        asistencia(ana, turno, true).andExpect(status().isOk());
        assertThat(estado(turno)).isEqualTo("completado");

        reloj.fijar(Instant.parse("2026-11-18T13:00:00Z"));   // miércoles
        asistencia(ana, turno, false)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Solo se puede corregir el mismo día del turno."));
        assertThat(estado(turno)).isEqualTo("completado");
    }

    @Test
    @DisplayName("HU-30: un profesional no marca turnos de otro (404)")
    void profesionalNoMarcaLosDeOtro() throws Exception {
        UUID deBruno = turno(brunoId, luciaId, "2026-11-17T10:00", "confirmado");

        asistencia(ana, deBruno, true).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("HU-31 CA1: la lista trae nombre, teléfono, visitas, faltas y última visita, y se busca por nombre o teléfono")
    void listaYBusqueda() throws Exception {
        cliente("Martín", "+5491155554444");
        cliente("Pedro", null);

        clientes(admin, null).andExpect(jsonPath("$[*].nombre", contains("Lucía", "Martín", "Pedro")));
        clientes(admin, "mart").andExpect(jsonPath("$[*].nombre", contains("Martín")));
        clientes(admin, "2345-6789")
                .andExpect(jsonPath("$[*].nombre", contains("Lucía")))
                .andExpect(jsonPath("$[0].telefono").value("+5491123456789"));
    }

    @Test
    @DisplayName("HU-31 CA2: el detalle trae el historial de turnos del cliente, del más nuevo al más viejo")
    void historial() throws Exception {
        UUID viejo = turno(anaId, luciaId, "2026-11-03T10:00", "completado");
        UUID nuevo = turno(brunoId, luciaId, "2026-11-17T10:00", "ausente");

        mockMvc.perform(get("/api/admin/clientes/" + luciaId).header(HttpHeaders.HOST, "demo.localhost").cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cliente.nombre").value("Lucía"))
                .andExpect(jsonPath("$.turnos[*].id", contains(nuevo.toString(), viejo.toString())))
                .andExpect(jsonPath("$.turnos[0].profesionalNombre").value("Bruno"))
                .andExpect(jsonPath("$.turnos[0].servicios", contains("Corte")))
                .andExpect(jsonPath("$.turnos[0].precioTotal").value(8000));
    }

    @Test
    @DisplayName("HU-31 CA3: un profesional no ve la lista de clientes (403)")
    void profesionalNoVeClientes() throws Exception {
        clientes(ana, null).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("HU-34 CA1: borrar los datos anonimiza al cliente y sus turnos pasados quedan, sin datos personales")
    void borrarDatos() throws Exception {
        UUID pasado = turno(anaId, luciaId, "2026-11-03T10:00", "completado");
        jdbc.update("INSERT INTO acceso_cliente (local_id, cliente_id, token_hash, creado_en, vence_en) VALUES (?, ?, 'h', now(), now() + interval '1 day')",
                demoId, luciaId);

        mockMvc.perform(post("/api/admin/clientes/" + luciaId + "/borrar-datos").header(HttpHeaders.HOST, "demo.localhost")
                        .cookie(admin).with(PanelDeTest.conCsrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Cliente borrado"))
                .andExpect(jsonPath("$.telefono").doesNotExist())
                .andExpect(jsonPath("$.visitas").value(1))
                .andExpect(jsonPath("$.datosBorrados").value(true));

        assertThat(jdbc.queryForObject("SELECT telefono IS NULL AND email IS NULL FROM cliente WHERE id = ?", Boolean.class, luciaId)).isTrue();
        assertThat(estado(pasado)).isEqualTo("completado");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM acceso_cliente", Integer.class)).isZero();
    }

    @Test
    @DisplayName("HU-34 CA2: con turnos por delante, pide cancelarlos antes y no borra nada")
    void conTurnosPorDelante() throws Exception {
        turno(anaId, luciaId, "2026-11-20T10:00", "confirmado");

        mockMvc.perform(post("/api/admin/clientes/" + luciaId + "/borrar-datos").header(HttpHeaders.HOST, "demo.localhost")
                        .cookie(admin).with(PanelDeTest.conCsrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("TURNOS_FUTUROS"))
                .andExpect(jsonPath("$.detail").value("Tiene 1 turno por delante. Cancelalos antes de borrar sus datos."));
        assertThat(jdbc.queryForObject("SELECT nombre FROM cliente WHERE id = ?", String.class, luciaId)).isEqualTo("Lucía");
    }

    @Test
    @DisplayName("HU-04: el cliente de otro local no se ve desde este panel (404)")
    void clienteDeOtroLocal() throws Exception {
        UUID otraId = jdbc.queryForObject("INSERT INTO local (nombre, subdominio) VALUES ('Otra', 'otra') RETURNING id", UUID.class);
        UUID ajeno = jdbc.queryForObject("INSERT INTO cliente (local_id, nombre) VALUES (?, 'Ajeno') RETURNING id", UUID.class, otraId);

        mockMvc.perform(get("/api/admin/clientes/" + ajeno).header(HttpHeaders.HOST, "demo.localhost").cookie(admin))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("CLIENTE_NO_ENCONTRADO"));
        clientes(admin, null).andExpect(jsonPath("$", hasSize(1)));
    }

    private ResultActions asistencia(Cookie sesion, UUID turno, boolean vino) throws Exception {
        return mockMvc.perform(post("/api/panel/turnos/" + turno + "/asistencia").header(HttpHeaders.HOST, "demo.localhost")
                .cookie(sesion).contentType(MediaType.APPLICATION_JSON).content("{\"vino\": " + vino + "}").with(PanelDeTest.conCsrf()));
    }

    private ResultActions clientes(Cookie sesion, String buscar) throws Exception {
        var pedido = get("/api/admin/clientes").header(HttpHeaders.HOST, "demo.localhost").cookie(sesion);
        if (buscar != null) {
            pedido.param("buscar", buscar);
        }
        return mockMvc.perform(pedido);
    }

    private UUID cliente(String nombre, String telefono) {
        return jdbc.queryForObject("INSERT INTO cliente (local_id, nombre, telefono) VALUES (?, ?, ?) RETURNING id",
                UUID.class, demoId, nombre, telefono);
    }

    private UUID turno(UUID profesional, UUID cliente, String inicio, String estado) {
        UUID id = jdbc.queryForObject("""
                INSERT INTO turno (local_id, profesional_id, cliente_id, tipo, estado, inicio, fin, ocupado_hasta)
                VALUES (?, ?, ?, 'manual', ?, ?::timestamptz, ?::timestamptz + interval '30 minutes', ?::timestamptz + interval '30 minutes')
                RETURNING id
                """, UUID.class, demoId, profesional, cliente, estado, inicio + "-03:00", inicio + "-03:00", inicio + "-03:00");
        jdbc.update("INSERT INTO turno_servicio (local_id, turno_id, servicio_id, duracion_min, precio) VALUES (?, ?, ?, 30, 8000)",
                demoId, id, corteId);
        return id;
    }

    private String estado(UUID turno) {
        return jdbc.queryForObject("SELECT estado FROM turno WHERE id = ?", String.class, turno);
    }
}
