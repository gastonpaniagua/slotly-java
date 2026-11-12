package ar.com.slotly.reserva;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

import ar.com.slotly.BaseDeTest;
import ar.com.slotly.IntegrationTest;
import ar.com.slotly.RelojDeTest;
import ar.com.slotly.error.SlotlyException;
import ar.com.slotly.local.Local;
import ar.com.slotly.local.LocalResolver;
import ar.com.slotly.seguridad.LimiteDePedidos;

// POST /api/publico/apartados contra Postgres real, con el reloj fijo (lunes 16/11/2026 9:00 en Buenos Aires).
// Local demo con apartado de 5 min. Ana (orden 1) y Bruno (orden 2) trabajan los martes de 10 a 13;
// los dos hacen Corte (30 min, $ 8.000) y solo Ana hace Barba (30 min, $ 5.000).
@IntegrationTest
class ApartadoControllerTest {

    private static final String MARTES = "2026-11-17";
    private static final Instant LUNES_9 = Instant.parse("2026-11-16T12:00:00Z");

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    RelojDeTest reloj;

    @Autowired
    ApartadoService apartados;

    @Autowired
    LocalResolver localResolver;

    UUID demoId;
    UUID corteId;
    UUID barbaId;
    UUID anaId;
    UUID brunoId;

    @Autowired
    LimiteDePedidos limite;

    @BeforeEach
    void prepararBase() {
        reloj.fijar(LUNES_9);
        limite.reiniciar();   // cada test arranca sin pedidos contados (HU-15)
        BaseDeTest.vaciar(jdbc);
        demoId = jdbc.queryForObject("INSERT INTO local (nombre, subdominio) VALUES ('Demo', 'demo') RETURNING id", UUID.class);
        corteId = crearServicio("Corte", 8000);
        barbaId = crearServicio("Barba", 5000);
        anaId = crearProfesional("Ana", 1);
        brunoId = crearProfesional("Bruno", 2);
        hace(anaId, corteId);
        hace(anaId, barbaId);
        hace(brunoId, corteId);
        trabajaLosMartes(anaId);
        trabajaLosMartes(brunoId);
    }

    @Test
    @DisplayName("HU-11: aparta el horario por 5 minutos y devuelve el token una sola vez (en la base, solo su hash)")
    void apartaElHorario() throws Exception {
        MvcResult resultado = apartar(List.of(corteId, barbaId), anaId.toString(), "10:00", null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.venceEn").value("2026-11-16T12:05:00Z"))
                .andExpect(jsonPath("$.segundosParaConfirmar").value(300))
                .andExpect(jsonPath("$.profesionalNombre").value("Ana"))
                .andExpect(jsonPath("$.fecha").value(MARTES))
                .andExpect(jsonPath("$.hora").value("10:00"))
                .andExpect(jsonPath("$.duracionMin").value(60))
                .andExpect(jsonPath("$.precioTotal").value(13000))
                .andReturn();

        String id = JsonPath.read(resultado.getResponse().getContentAsString(), "$.id");
        String token = JsonPath.read(resultado.getResponse().getContentAsString(), "$.token");
        Map<String, Object> fila = jdbc.queryForMap("SELECT estado, tipo, token_hash, cliente_id FROM turno WHERE id = ?::uuid", id);
        assertThat(fila.get("estado")).isEqualTo("apartado");
        assertThat(fila.get("tipo")).isEqualTo("reserva");
        assertThat(fila.get("cliente_id")).isNull();
        assertThat(fila.get("token_hash")).isEqualTo(TokenSecreto.hash(token)).isNotEqualTo(token);
    }

    @Test
    @DisplayName("HU-10 CA5: el apartado trae los datos de transferencia del local para el resumen del paso 5")
    void traeLosDatosDeTransferencia() throws Exception {
        jdbc.update("UPDATE local SET alias = 'barberia.demo', cbu = '0000003100000000000001', titular_cuenta = 'Juan Pérez'");

        apartar(List.of(corteId), anaId.toString(), "10:00", null)
                .andExpect(jsonPath("$.transferencia.alias").value("barberia.demo"))
                .andExpect(jsonPath("$.transferencia.cbu").value("0000003100000000000001"))
                .andExpect(jsonPath("$.transferencia.titular").value("Juan Pérez"));
    }

    @Test
    @DisplayName("HU-10 CA8: la duración y el precio de cada servicio quedan copiados en el turno")
    void copiaDuracionYPrecio() throws Exception {
        String id = idDe(apartar(List.of(corteId, barbaId), anaId.toString(), "10:00", null));

        // Aunque el local cambie el precio después, el turno guarda el del momento.
        jdbc.update("UPDATE servicio SET precio = 99999");

        List<Map<String, Object>> copiados = jdbc.queryForList(
                "SELECT duracion_min, precio FROM turno_servicio WHERE turno_id = ?::uuid ORDER BY precio", id);
        assertThat(copiados).extracting(f -> f.get("duracion_min"), f -> f.get("precio"))
                .containsExactly(org.assertj.core.groups.Tuple.tuple(30, 5000), org.assertj.core.groups.Tuple.tuple(30, 8000));
    }

    @Test
    @DisplayName("HU-11 CA1: con el horario apartado, esa hora deja de ofrecerse a otros")
    void dejaDeOfrecerse() throws Exception {
        apartar(List.of(corteId), anaId.toString(), "10:00", null).andExpect(status().isCreated());

        horasDe(anaId).andExpect(jsonPath("$.horas", not(hasItem("10:00"))));
    }

    @Test
    @DisplayName("HU-11 CA5 y HU-12 CA3: apartar una hora ya apartada por otro responde 409 HORARIO_OCUPADO")
    void horaYaApartada() throws Exception {
        apartar(List.of(corteId), anaId.toString(), "10:00", null).andExpect(status().isCreated());

        apartar(List.of(corteId), anaId.toString(), "10:00", null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("HORARIO_OCUPADO"))
                .andExpect(jsonPath("$.detail").value("Ese horario se acaba de ocupar, elegí otro."));
    }

    @Test
    @DisplayName("HU-11 CA2: pasados los 5 minutos la hora se vuelve a ofrecer y otro la puede apartar")
    void elApartadoVence() throws Exception {
        String primero = idDe(apartar(List.of(corteId), anaId.toString(), "10:00", null));

        reloj.fijar(LUNES_9.plus(Duration.ofMinutes(6)));

        horasDe(anaId).andExpect(jsonPath("$.horas", hasItem("10:00")));
        apartar(List.of(corteId), anaId.toString(), "10:00", null).andExpect(status().isCreated());
        // El vencido quedó marcado como tal (si no, el constraint no habría dejado entrar al nuevo).
        assertThat(jdbc.queryForObject("SELECT estado FROM turno WHERE id = ?::uuid", String.class, primero))
                .isEqualTo("vencido");
    }

    @Test
    @DisplayName("HU-11 CA3: al elegir otra hora con el apartado anterior, el anterior se libera")
    void liberaElAnterior() throws Exception {
        MvcResult primero = apartar(List.of(corteId), anaId.toString(), "10:00", null).andReturn();
        String id = JsonPath.read(primero.getResponse().getContentAsString(), "$.id");
        String token = JsonPath.read(primero.getResponse().getContentAsString(), "$.token");

        apartar(List.of(corteId), anaId.toString(), "11:00", "{\"id\": \"" + id + "\", \"token\": \"" + token + "\"}")
                .andExpect(status().isCreated());

        horasDe(anaId)
                .andExpect(jsonPath("$.horas", hasItem("10:00")))
                .andExpect(jsonPath("$.horas", not(hasItem("11:00"))));
    }

    @Test
    @DisplayName("HU-11 CA3: con un token que no corresponde, el apartado de otro no se libera")
    void noLiberaElDeOtro() throws Exception {
        String id = idDe(apartar(List.of(corteId), anaId.toString(), "10:00", null));

        apartar(List.of(corteId), anaId.toString(), "11:00", "{\"id\": \"" + id + "\", \"token\": \"inventado\"}")
                .andExpect(status().isCreated());

        horasDe(anaId).andExpect(jsonPath("$.horas", not(hasItem("10:00"))));
    }

    @Test
    @DisplayName("HU-07 CA9: con Cualquiera disponible se asigna al que tenga menos turnos ese día")
    void cualquieraAlQueTieneMenosTurnos() throws Exception {
        // Ana ya tiene un turno el martes; Bruno ninguno.
        apartar(List.of(corteId), anaId.toString(), "12:00", null).andExpect(status().isCreated());

        apartar(List.of(corteId), "cualquiera", "10:00", null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.profesionalNombre").value("Bruno"));
    }

    @Test
    @DisplayName("HU-07 CA9: con Cualquiera disponible y empate, se asigna al primero por orden")
    void cualquieraEmpateAlPrimeroPorOrden() throws Exception {
        apartar(List.of(corteId), "cualquiera", "10:00", null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.profesionalNombre").value("Ana"));
    }

    @Test
    @DisplayName("HU-07 CA9: Cualquiera disponible no le asigna a quien no está libre a esa hora")
    void cualquieraSoloEntreLosLibres() throws Exception {
        apartar(List.of(corteId), anaId.toString(), "10:00", null).andExpect(status().isCreated());

        apartar(List.of(corteId), "cualquiera", "10:00", null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.profesionalNombre").value("Bruno"));
        // Ya no queda nadie libre a las 10:00.
        apartar(List.of(corteId), "cualquiera", "10:00", null).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Una hora que no se ofrece (fuera del horario, fuera del intervalo o sin anticipación) responde 409")
    void horaQueNoSeOfrece() throws Exception {
        apartar(List.of(corteId), anaId.toString(), "14:00", null).andExpect(status().isConflict());
        apartar(List.of(corteId), anaId.toString(), "10:07", null).andExpect(status().isConflict());
        reloj.fijar(Instant.parse("2026-11-17T13:20:00Z"));   // martes 10:20: con 1 h de anticipación, 11:00 no
        apartar(List.of(corteId), anaId.toString(), "11:00", null).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("HU-15 CA3 y validaciones: campos que faltan, que no existen o mal formados responden 400")
    void pedidoInvalido() throws Exception {
        enviar("{\"servicios\": [], \"profesional\": \"cualquiera\", \"fecha\": \"" + MARTES + "\", \"hora\": \"10:00\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("servicios"));
        enviar("{\"servicios\": [\"" + corteId + "\"], \"profesional\": \"cualquiera\", \"fecha\": \"" + MARTES
                + "\", \"hora\": \"10:00\", \"estado\": \"confirmado\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"));
        enviar("{\"servicios\": [\"" + corteId + "\"], \"profesional\": \"cualquiera\", \"fecha\": \"17/11/2026\", \"hora\": \"10:00\"}")
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("HU-13 CA4: 10 apartados simultáneos del mismo horario y profesional: entra exactamente uno")
    void diezSimultaneos() throws Exception {
        Local local = localResolver.resolver("demo.localhost");
        ApartadoService.Pedido pedido = new ApartadoService.Pedido(Set.of(corteId), anaId.toString(),
                LocalDate.parse(MARTES), LocalTime.of(10, 0), null);
        int cantidad = 10;
        CountDownLatch largada = new CountDownLatch(1);
        ExecutorService hilos = Executors.newFixedThreadPool(cantidad);
        List<Future<String>> resultados = new ArrayList<>();
        try {
            for (int i = 0; i < cantidad; i++) {
                resultados.add(hilos.submit(() -> {
                    largada.await();   // todos arrancan juntos
                    try {
                        apartados.apartar(local, pedido);
                        return "OK";
                    } catch (SlotlyException e) {
                        return e.getCodigo();
                    }
                }));
            }
            largada.countDown();
            List<String> respuestas = new ArrayList<>();
            for (Future<String> resultado : resultados) {
                respuestas.add(resultado.get());
            }

            assertThat(respuestas).filteredOn("OK"::equals).hasSize(1);
            assertThat(respuestas).filteredOn("HORARIO_OCUPADO"::equals).hasSize(cantidad - 1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM turno", Integer.class)).isEqualTo(1);
        } finally {
            hilos.shutdownNow();
        }
    }

    // ---- Ayudas ----

    private ResultActions apartar(List<UUID> servicios, String profesional, String hora, String anterior) throws Exception {
        String lista = String.join(",", servicios.stream().map(s -> "\"" + s + "\"").toList());
        String cuerpo = "{\"servicios\": [" + lista + "], \"profesional\": \"" + profesional + "\", \"fecha\": \""
                + MARTES + "\", \"hora\": \"" + hora + "\"" + (anterior == null ? "" : ", \"anterior\": " + anterior) + "}";
        return enviar(cuerpo);
    }

    private ResultActions enviar(String cuerpo) throws Exception {
        return mockMvc.perform(post("/api/publico/apartados")
                .header(HttpHeaders.HOST, "demo.localhost")
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo));
    }

    private String idDe(ResultActions resultado) throws Exception {
        return JsonPath.read(resultado.andReturn().getResponse().getContentAsString(), "$.id");
    }

    private ResultActions horasDe(UUID profesionalId) throws Exception {
        return mockMvc.perform(get("/api/publico/disponibilidad/horas")
                .header(HttpHeaders.HOST, "demo.localhost")
                .param("servicios", corteId.toString())
                .param("profesional", profesionalId.toString())
                .param("fecha", MARTES));
    }

    private UUID crearServicio(String nombre, int precio) {
        return jdbc.queryForObject("""
                INSERT INTO servicio (local_id, nombre, duracion_min, precio) VALUES (?, ?, 30, ?) RETURNING id
                """, UUID.class, demoId, nombre, precio);
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
}
