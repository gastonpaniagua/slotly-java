package ar.com.slotly.disponibilidad;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import ar.com.slotly.BaseDeTest;
import ar.com.slotly.IntegrationTest;
import ar.com.slotly.RelojDeTest;

// /api/publico/disponibilidad de punta a punta, contra Postgres real y con el reloj fijo.
// Local demo de Buenos Aires con las reglas por defecto: intervalo 15, anticipación 60, tope 30, limpieza 0.
// Ana y Bruno trabajan los martes de 10 a 13. Los dos hacen Corte (30 min); solo Ana hace Barba (30 min).
@IntegrationTest
class DisponibilidadControllerTest {

    private static final String MARTES = "2026-11-17";
    // Lunes 16/11/2026 a las 9:00 en Buenos Aires.
    private static final Instant LUNES_9 = Instant.parse("2026-11-16T12:00:00Z");

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    RelojDeTest reloj;

    UUID demoId;
    UUID corteId;
    UUID barbaId;
    UUID anaId;
    UUID brunoId;

    @BeforeEach
    void prepararBase() {
        reloj.fijar(LUNES_9);
        BaseDeTest.vaciar(jdbc);
        demoId = jdbc.queryForObject("INSERT INTO local (nombre, subdominio) VALUES ('Demo', 'demo') RETURNING id", UUID.class);
        corteId = crearServicio("Corte", null);
        barbaId = crearServicio("Barba", null);
        anaId = crearProfesional("Ana", 1);
        brunoId = crearProfesional("Bruno", 2);
        hace(anaId, corteId);
        hace(anaId, barbaId);
        hace(brunoId, corteId);
        trabajaLosMartes(anaId);
        trabajaLosMartes(brunoId);
    }

    @Test
    @DisplayName("HU-07: devuelve las horas libres del profesional en hora del local, cada 15 min hasta la última que entra")
    void horasDeUnProfesional() throws Exception {
        horas(corteId.toString(), anaId.toString(), MARTES)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fecha").value(MARTES))
                .andExpect(jsonPath("$.horas[0]").value("10:00"))
                .andExpect(jsonPath("$.horas[-1:]", contains("12:30")))
                .andExpect(jsonPath("$.horas.length()").value(11));
    }

    @Test
    @DisplayName("HU-07 CA3: un turno guardado en la base saca las horas que se cruzan")
    void turnoDeLaBase() throws Exception {
        ocupar(anaId, "confirmado", "10:00", "10:45", null);

        horas(corteId.toString(), anaId.toString(), MARTES)
                .andExpect(jsonPath("$.horas", not(hasItem("10:00"))))
                .andExpect(jsonPath("$.horas", not(hasItem("10:30"))))
                .andExpect(jsonPath("$.horas", hasItem("10:45")));
    }

    @Test
    @DisplayName("HU-07 CA5: un apartado vigente de otro cliente saca el horario; uno vencido no")
    void apartadosVigentesYVencidos() throws Exception {
        ocupar(anaId, "apartado", "11:00", "11:30", "2026-11-16T12:05:00Z");   // vence en 5 min: vigente
        ocupar(anaId, "apartado", "12:00", "12:30", "2026-11-16T11:55:00Z");   // venció hace 5 min

        horas(corteId.toString(), anaId.toString(), MARTES)
                .andExpect(jsonPath("$.horas", not(hasItem("11:00"))))
                .andExpect(jsonPath("$.horas", hasItem("12:00")));
    }

    @Test
    @DisplayName("HU-07 CA6: con el reloj a las 10:20 del martes, la primera hora de ese día es 11:30")
    void anticipacionConElRelojDeLaApp() throws Exception {
        reloj.fijar(Instant.parse("2026-11-17T13:20:00Z"));   // martes 10:20 en Buenos Aires

        horas(corteId.toString(), anaId.toString(), MARTES)
                .andExpect(jsonPath("$.horas[0]").value("11:30"));
    }

    @Test
    @DisplayName("HU-07 CA9: con Cualquiera disponible aparece la hora si al menos uno está libre")
    void cualquieraDisponible() throws Exception {
        ocupar(anaId, "confirmado", "10:00", "11:00", null);

        horas(corteId.toString(), DisponibilidadService.CUALQUIERA, MARTES)
                .andExpect(jsonPath("$.horas", hasItems("10:00", "10:30")));   // Bruno está libre
    }

    @Test
    @DisplayName("HU-07 CA9: Cualquiera disponible solo cuenta a los que hacen todos los servicios")
    void cualquieraSoloConLosQueHacenTodo() throws Exception {
        ocupar(anaId, "confirmado", "10:00", "11:00", null);

        // Bruno está libre a las 10:00, pero no hace Barba.
        horas(corteId + "," + barbaId, DisponibilidadService.CUALQUIERA, MARTES)
                .andExpect(jsonPath("$.horas", not(hasItem("10:00"))))
                .andExpect(jsonPath("$.horas[0]").value("11:00"));
    }

    @Test
    @DisplayName("Varios servicios suman su duración; la limpieza propia de un servicio no pisa el turno siguiente")
    void duracionYLimpiezaDeLosServicios() throws Exception {
        UUID colorId = crearServicio("Color", 10);   // 30 min con 10 de limpieza
        hace(anaId, colorId);
        ocupar(anaId, "confirmado", "11:00", "11:30", null);

        // Corte + Barba = 60 min: la última hora es 12:00.
        horas(corteId + "," + barbaId, anaId.toString(), MARTES)
                .andExpect(jsonPath("$.horas[-1:]", contains("12:00")));
        // Color a las 10:30 terminaría 11:00 y con limpieza ocuparía hasta 11:10: pisa el turno de las 11.
        horas(colorId.toString(), anaId.toString(), MARTES)
                .andExpect(jsonPath("$.horas", hasItem("10:15")))
                .andExpect(jsonPath("$.horas", not(hasItem("10:30"))));
    }

    @Test
    @DisplayName("HU-07 CA11: un servicio de otro local responde 404 SERVICIO_NO_ENCONTRADO")
    void servicioDeOtroLocal() throws Exception {
        UUID otraId = jdbc.queryForObject("INSERT INTO local (nombre, subdominio) VALUES ('Otra', 'otra') RETURNING id", UUID.class);
        UUID servicioDeOtra = jdbc.queryForObject(
                "INSERT INTO servicio (local_id, nombre, duracion_min, precio) VALUES (?, 'Corte', 30, 8000) RETURNING id",
                UUID.class, otraId);

        horas(servicioDeOtra.toString(), DisponibilidadService.CUALQUIERA, MARTES)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("SERVICIO_NO_ENCONTRADO"));
    }

    @Test
    @DisplayName("HU-07 CA11: un profesional que no hace el servicio, de otro local o inexistente responde 404 PROFESIONAL_NO_ENCONTRADO")
    void profesionalQueNoCorresponde() throws Exception {
        horas(barbaId.toString(), brunoId.toString(), MARTES)   // Bruno no hace Barba
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("PROFESIONAL_NO_ENCONTRADO"));
        horas(corteId.toString(), UUID.randomUUID().toString(), MARTES)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("PROFESIONAL_NO_ENCONTRADO"));
    }

    @Test
    @DisplayName("HU-07 CA11: parámetros mal formados responden 400, nunca 500")
    void parametrosInvalidos() throws Exception {
        horas(corteId.toString(), "no-es-un-id", MARTES).andExpect(status().isBadRequest());
        horas(corteId.toString(), anaId.toString(), "17/11/2026").andExpect(status().isBadRequest());
        horas("", anaId.toString(), MARTES).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("HU-08 CA1 y CA2: en la semana, solo el martes tiene lugar; también devuelve hoy y el último día")
    void diasConLugar() throws Exception {
        dias(corteId.toString(), DisponibilidadService.CUALQUIERA, "2026-11-16", "2026-11-22")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hoy").value("2026-11-16"))
                .andExpect(jsonPath("$.ultimoDia").value("2026-12-16"))
                .andExpect(jsonPath("$.dias[*].fecha", contains("2026-11-16", "2026-11-17", "2026-11-18",
                        "2026-11-19", "2026-11-20", "2026-11-21", "2026-11-22")))
                .andExpect(jsonPath("$.dias[*].hayLugar", contains(false, true, false, false, false, false, false)));
    }

    @Test
    @DisplayName("HU-08: sin fechas, arranca el lunes de esta semana en el local y trae 6 semanas")
    void diasSinFechas() throws Exception {
        reloj.fijar(Instant.parse("2026-11-19T02:00:00Z"));   // jueves 19 en UTC, pero miércoles 18 23:00 en Buenos Aires

        mockMvc.perform(get("/api/publico/disponibilidad/dias")
                        .header(HttpHeaders.HOST, "demo.localhost")
                        .param("servicios", corteId.toString())
                        .param("profesional", DisponibilidadService.CUALQUIERA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hoy").value("2026-11-18"))
                .andExpect(jsonPath("$.dias[0].fecha").value("2026-11-16"))
                .andExpect(jsonPath("$.dias.length()").value(42));
    }

    @Test
    @DisplayName("HU-08 CA1: un martes completo (los dos bloqueados todo el día) aparece sin lugar")
    void diaCompleto() throws Exception {
        ocupar(anaId, "confirmado", "10:00", "13:00", null);
        ocupar(brunoId, "confirmado", "10:00", "13:00", null);

        dias(corteId.toString(), DisponibilidadService.CUALQUIERA, MARTES, MARTES)
                .andExpect(jsonPath("$.dias[0].hayLugar").value(false));
    }

    @Test
    @DisplayName("HU-08: un rango al revés o de más de 6 semanas responde 400")
    void rangoInvalido() throws Exception {
        dias(corteId.toString(), DisponibilidadService.CUALQUIERA, "2026-11-22", "2026-11-16")
                .andExpect(status().isBadRequest());
        dias(corteId.toString(), DisponibilidadService.CUALQUIERA, "2026-11-16", "2027-01-16")
                .andExpect(status().isBadRequest());
    }

    // ---- Ayudas ----

    private ResultActions horas(String servicios, String profesional, String fecha) throws Exception {
        return mockMvc.perform(get("/api/publico/disponibilidad/horas")
                .header(HttpHeaders.HOST, "demo.localhost")
                .param("servicios", servicios)
                .param("profesional", profesional)
                .param("fecha", fecha));
    }

    private ResultActions dias(String servicios, String profesional, String desde, String hasta) throws Exception {
        return mockMvc.perform(get("/api/publico/disponibilidad/dias")
                .header(HttpHeaders.HOST, "demo.localhost")
                .param("servicios", servicios)
                .param("profesional", profesional)
                .param("desde", desde)
                .param("hasta", hasta));
    }

    private UUID crearServicio(String nombre, Integer limpiezaMin) {
        return jdbc.queryForObject("""
                INSERT INTO servicio (local_id, nombre, duracion_min, precio, limpieza_min) VALUES (?, ?, 30, 8000, ?) RETURNING id
                """, UUID.class, demoId, nombre, limpiezaMin);
    }

    private UUID crearProfesional(String nombre, int orden) {
        return jdbc.queryForObject("INSERT INTO profesional (local_id, nombre, orden) VALUES (?, ?, ?) RETURNING id",
                UUID.class, demoId, nombre, orden);
    }

    private void hace(UUID profesionalId, UUID servicioId) {
        jdbc.update("INSERT INTO profesional_servicio (local_id, profesional_id, servicio_id) VALUES (?, ?, ?)",
                demoId, profesionalId, servicioId);
    }

    private void trabajaLosMartes(UUID profesionalId) {
        jdbc.update("""
                INSERT INTO horario (local_id, profesional_id, dia_semana, hora_inicio, hora_fin) VALUES (?, ?, 2, '10:00', '13:00')
                """, demoId, profesionalId);
    }

    // Ocupa al profesional el martes de desde a hasta (hora de Buenos Aires, UTC-3). venceEn solo para apartados.
    private void ocupar(UUID profesionalId, String estado, String desde, String hasta, String venceEn) {
        String tipo = "apartado".equals(estado) ? "reserva" : "bloqueo";
        jdbc.update("""
                INSERT INTO turno (local_id, profesional_id, tipo, estado, inicio, fin, ocupado_hasta, vence_en)
                VALUES (?, ?, ?, ?, ?::timestamptz, ?::timestamptz, ?::timestamptz, ?::timestamptz)
                """, demoId, profesionalId, tipo, estado,
                MARTES + "T" + desde + ":00-03:00", MARTES + "T" + hasta + ":00-03:00", MARTES + "T" + hasta + ":00-03:00",
                venceEn);
    }
}
