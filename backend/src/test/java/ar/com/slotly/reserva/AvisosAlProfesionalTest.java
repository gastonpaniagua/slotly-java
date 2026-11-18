package ar.com.slotly.reserva;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;

import jakarta.servlet.http.Cookie;

import ar.com.slotly.BaseDeTest;
import ar.com.slotly.IntegrationTest;
import ar.com.slotly.RelojDeTest;
import ar.com.slotly.local.Local;
import ar.com.slotly.local.LocalResolver;
import ar.com.slotly.panel.PanelDeTest;
import ar.com.slotly.reserva.ApartadoService.Apartado;
import ar.com.slotly.reserva.ConfirmacionService.DatosDelCliente;
import ar.com.slotly.seguridad.IntentosDeIngreso;
import ar.com.slotly.seguridad.LimiteDePedidos;

// HU-37: los avisos al profesional en el panel, contra la app completa. Lo que hace el cliente desde la web y lo que
// hace el admin en la agenda de otro le avisan; lo que hace el profesional en su propia agenda, no.
// Reloj: lunes 16/11/2026 a las 9:00 en Buenos Aires; cada acción corre el reloj un minuto, así el orden de la lista
// es el de las acciones. Ana y Bruno atienden el martes de 10 a 13.
@IntegrationTest
class AvisosAlProfesionalTest {

    private static final LocalDate MARTES = LocalDate.of(2026, 11, 17);
    private static final Instant LUNES_9 = Instant.parse("2026-11-16T12:00:00Z");
    private static final String CLAVE = "clave";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    RelojDeTest reloj;

    @Autowired
    LimiteDePedidos limite;

    @Autowired
    IntentosDeIngreso intentos;

    @Autowired
    PasswordEncoder encoder;

    @Autowired
    ApartadoService apartados;

    @Autowired
    ConfirmacionService confirmaciones;

    @Autowired
    LocalResolver localResolver;

    UUID demoId;
    UUID corteId;
    UUID anaId;
    UUID brunoId;
    Instant ahora;
    Cookie admin;
    Cookie ana;
    Cookie bruno;

    @BeforeEach
    void preparar() throws Exception {
        ahora = LUNES_9;
        reloj.fijar(ahora);
        limite.reiniciar();
        intentos.reiniciar();
        BaseDeTest.vaciar(jdbc);
        demoId = jdbc.queryForObject("INSERT INTO local (nombre, subdominio) VALUES ('Demo', 'demo') RETURNING id", UUID.class);
        corteId = jdbc.queryForObject(
                "INSERT INTO servicio (local_id, nombre, duracion_min, precio) VALUES (?, 'Corte', 30, 8000) RETURNING id",
                UUID.class, demoId);
        anaId = profesional("Ana");
        brunoId = profesional("Bruno");
        usuario("duenio@demo.com", "admin", null);
        usuario("ana@demo.com", "profesional", anaId);
        usuario("bruno@demo.com", "profesional", brunoId);
        admin = PanelDeTest.ingresar(mockMvc, "demo", "duenio@demo.com", CLAVE);
        ana = PanelDeTest.ingresar(mockMvc, "demo", "ana@demo.com", CLAVE);
        bruno = PanelDeTest.ingresar(mockMvc, "demo", "bruno@demo.com", CLAVE);
    }

    @Test
    @DisplayName("HU-37 CA1: cuando un cliente reserva, el profesional ve el aviso con el detalle y el contador en 1")
    void reservaDelCliente() throws Exception {
        reservar(brunoId, "10:00");

        avisos(bruno)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sinLeer").value(1))
                .andExpect(jsonPath("$.notificaciones", hasSize(1)))
                .andExpect(jsonPath("$.notificaciones[0].tipo").value("reserva"))
                .andExpect(jsonPath("$.notificaciones[0].fecha").value("2026-11-17"))
                .andExpect(jsonPath("$.notificaciones[0].hora").value("10:00"))
                .andExpect(jsonPath("$.notificaciones[0].fechaAnterior").isEmpty())
                .andExpect(jsonPath("$.notificaciones[0].clienteNombre").value("Lucía"))
                .andExpect(jsonPath("$.notificaciones[0].servicios", contains("Corte")))
                .andExpect(jsonPath("$.notificaciones[0].creadaEn").value("2026-11-16T09:01"))
                .andExpect(jsonPath("$.notificaciones[0].leida").value(false));
    }

    @Test
    @DisplayName("HU-37 CA1: cuando el cliente reprograma o cancela, también avisa, con el horario anterior; la más nueva primero")
    void reprogramaYCancelaElCliente() throws Exception {
        Apartado turno = reservar(brunoId, "10:00");
        String json = mockMvc.perform(conToken(post("/api/publico/turnos/" + turno.id() + "/reprogramar"), turno.token())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"fecha\": \"2026-11-17\", \"hora\": \"11:00\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String nuevo = JsonPath.read(json, "$.id");
        String tokenNuevo = JsonPath.read(json, "$.token");
        mockMvc.perform(conToken(post("/api/publico/turnos/" + nuevo + "/cancelar"), tokenNuevo)).andExpect(status().isOk());

        avisos(bruno)
                .andExpect(jsonPath("$.sinLeer").value(3))
                .andExpect(jsonPath("$.notificaciones[*].tipo", contains("cancelacion", "reprogramacion", "reserva")))
                .andExpect(jsonPath("$.notificaciones[1].hora").value("11:00"))
                .andExpect(jsonPath("$.notificaciones[1].fechaAnterior").value("2026-11-17"))
                .andExpect(jsonPath("$.notificaciones[1].horaAnterior").value("10:00"));
    }

    @Test
    @DisplayName("HU-37 CA2: al marcarlas como leídas el contador vuelve a cero; uno nuevo lo vuelve a subir")
    void leidas() throws Exception {
        reservar(brunoId, "10:00");

        mockMvc.perform(post("/api/panel/notificaciones/leidas").header(HttpHeaders.HOST, "demo.localhost")
                        .cookie(bruno).with(PanelDeTest.conCsrf()))
                .andExpect(status().isNoContent());

        avisos(bruno)
                .andExpect(jsonPath("$.sinLeer").value(0))
                .andExpect(jsonPath("$.notificaciones[0].leida").value(true));
        reservar(brunoId, "11:00");
        avisos(bruno).andExpect(jsonPath("$.sinLeer").value(1));
    }

    @Test
    @DisplayName("HU-37 CA3: lo que el profesional hace en su propia agenda no le avisa; lo que hace el admin en ella, sí")
    void cambiosDesdeElPanel() throws Exception {
        String propio = manual(bruno, null, "10:00");
        mover(bruno, propio, "10:30");
        cancelar(bruno, propio);
        avisos(bruno).andExpect(jsonPath("$.sinLeer").value(0));

        String delAdmin = manual(admin, brunoId.toString(), "11:00");
        mover(admin, delAdmin, "12:00");
        cancelar(admin, delAdmin);

        avisos(bruno)
                .andExpect(jsonPath("$.sinLeer").value(3))
                .andExpect(jsonPath("$.notificaciones[*].tipo", contains("cancelacion", "movido", "reserva")))
                .andExpect(jsonPath("$.notificaciones[1].clienteNombre").value("Pedro"))
                .andExpect(jsonPath("$.notificaciones[1].hora").value("12:00"))
                .andExpect(jsonPath("$.notificaciones[1].horaAnterior").value("11:00"));
    }

    @Test
    @DisplayName("HU-37 CA1: cada aviso muestra el horario de ese momento, aunque después el turno se mueva")
    void elHorarioDeEseMomento() throws Exception {
        String turno = manual(admin, brunoId.toString(), "11:00");
        mover(admin, turno, "12:00");
        mover(bruno, turno, "12:30");

        avisos(bruno)
                .andExpect(jsonPath("$.notificaciones[*].tipo", contains("movido", "reserva")))
                .andExpect(jsonPath("$.notificaciones[0].hora").value("12:00"))
                .andExpect(jsonPath("$.notificaciones[0].horaAnterior").value("11:00"))
                .andExpect(jsonPath("$.notificaciones[1].hora").value("11:00"));
    }

    @Test
    @DisplayName("HU-37 CA4: cada profesional ve y marca como leídos solo los suyos; un admin sin agenda no tiene; sin sesión, 401")
    void soloLosSuyos() throws Exception {
        reservar(brunoId, "10:00");
        reservar(anaId, "10:00");

        avisos(ana)
                .andExpect(jsonPath("$.sinLeer").value(1))
                .andExpect(jsonPath("$.notificaciones", hasSize(1)));
        mockMvc.perform(post("/api/panel/notificaciones/leidas").header(HttpHeaders.HOST, "demo.localhost")
                .cookie(ana).with(PanelDeTest.conCsrf()));
        avisos(bruno).andExpect(jsonPath("$.sinLeer").value(1));
        avisos(admin)
                .andExpect(jsonPath("$.sinLeer").value(0))
                .andExpect(jsonPath("$.notificaciones", hasSize(0)));
        mockMvc.perform(get("/api/panel/notificaciones").header(HttpHeaders.HOST, "demo.localhost"))
                .andExpect(status().isUnauthorized());
    }

    private Apartado reservar(UUID profesional, String hora) {
        Local demo = localResolver.resolver("demo.localhost");
        Apartado apartado = apartados.apartar(demo, new ApartadoService.Pedido(Set.of(corteId), profesional.toString(),
                MARTES, LocalTime.parse(hora), null));
        avanzar();
        confirmaciones.confirmar(demo, apartado.id(), apartado.token(),
                new DatosDelCliente("Lucía", "11 2345-6789", null, "efectivo"));
        return apartado;
    }

    private String manual(Cookie sesion, String profesional, String hora) throws Exception {
        avanzar();
        String cuerpo = "{" + (profesional == null ? "" : "\"profesional\": \"" + profesional + "\", ")
                + "\"fecha\": \"2026-11-17\", \"hora\": \"" + hora + "\", \"servicioIds\": [\"" + corteId + "\"], "
                + "\"clienteNombre\": \"Pedro\"}";
        String json = mockMvc.perform(post("/api/panel/turnos").header(HttpHeaders.HOST, "demo.localhost").cookie(sesion)
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo).with(PanelDeTest.conCsrf()))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.id");
    }

    private void mover(Cookie sesion, String turno, String hora) throws Exception {
        avanzar();
        mockMvc.perform(post("/api/panel/turnos/" + turno + "/mover").header(HttpHeaders.HOST, "demo.localhost").cookie(sesion)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"fecha\": \"2026-11-17\", \"hora\": \"" + hora + "\"}")
                        .with(PanelDeTest.conCsrf()))
                .andExpect(status().isOk());
    }

    private void cancelar(Cookie sesion, String turno) throws Exception {
        avanzar();
        mockMvc.perform(post("/api/panel/turnos/" + turno + "/cancelar").header(HttpHeaders.HOST, "demo.localhost")
                        .cookie(sesion).with(PanelDeTest.conCsrf()))
                .andExpect(status().isOk());
    }

    // Un pedido del cliente con el token de su link, un minuto más tarde.
    private MockHttpServletRequestBuilder conToken(MockHttpServletRequestBuilder pedido, String token) {
        avanzar();
        return pedido.header(HttpHeaders.HOST, "demo.localhost").header(ConfirmacionController.HEADER_TOKEN, token);
    }

    private ResultActions avisos(Cookie sesion) throws Exception {
        return mockMvc.perform(get("/api/panel/notificaciones").header(HttpHeaders.HOST, "demo.localhost").cookie(sesion));
    }

    // Un minuto más tarde: cada aviso tiene su hora y la lista sale en el orden en que pasaron las cosas.
    private void avanzar() {
        ahora = ahora.plusSeconds(60);
        reloj.fijar(ahora);
    }

    private UUID profesional(String nombre) {
        UUID id = jdbc.queryForObject("INSERT INTO profesional (local_id, nombre) VALUES (?, ?) RETURNING id",
                UUID.class, demoId, nombre);
        jdbc.update("INSERT INTO profesional_servicio (local_id, profesional_id, servicio_id) VALUES (?, ?, ?)", demoId, id, corteId);
        jdbc.update("""
                INSERT INTO horario (local_id, profesional_id, dia_semana, hora_inicio, hora_fin) VALUES (?, ?, 2, '10:00', '13:00')
                """, demoId, id);
        return id;
    }

    private void usuario(String email, String rol, UUID profesional) {
        jdbc.update("INSERT INTO usuario (local_id, email, password_hash, rol, profesional_id) VALUES (?, ?, ?, ?, ?)",
                demoId, email, encoder.encode(CLAVE), rol, profesional);
    }
}
