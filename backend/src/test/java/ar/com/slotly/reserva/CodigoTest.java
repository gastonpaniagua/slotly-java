package ar.com.slotly.reserva;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

import ar.com.slotly.BaseDeTest;
import ar.com.slotly.CodigosEnviadosDeTest;
import ar.com.slotly.IntegrationTest;
import ar.com.slotly.RelojDeTest;
import ar.com.slotly.local.Local;
import ar.com.slotly.local.LocalResolver;
import ar.com.slotly.reserva.ApartadoService.Apartado;
import ar.com.slotly.reserva.ConfirmacionService.DatosDelCliente;
import ar.com.slotly.seguridad.LimiteDePedidos;

// HU-22: entrar con el teléfono y un código, contra la app completa. El "envío" de los tests guarda el código.
// Reloj: lunes 16/11/2026 a las 9:00 en Buenos Aires. Lucía (11 2345-6789) tiene un turno el martes a las 10:00.
@IntegrationTest
class CodigoTest {

    private static final Instant LUNES_9 = Instant.parse("2026-11-16T12:00:00Z");
    private static final String LUCIA = "+5491123456789";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    RelojDeTest reloj;

    @Autowired
    LimiteDePedidos limite;

    @Autowired
    CodigosEnviadosDeTest enviados;

    @Autowired
    ApartadoService apartados;

    @Autowired
    ConfirmacionService confirmaciones;

    @Autowired
    LocalResolver localResolver;

    UUID corteId;
    UUID anaId;
    Apartado turno;

    @BeforeEach
    void preparar() {
        reloj.fijar(LUNES_9);
        limite.reiniciar();
        enviados.reiniciar();
        BaseDeTest.vaciar(jdbc);
        UUID demoId = jdbc.queryForObject("INSERT INTO local (nombre, subdominio) VALUES ('Demo', 'demo') RETURNING id", UUID.class);
        jdbc.update("INSERT INTO local (nombre, subdominio) VALUES ('Otra', 'otra')");
        corteId = jdbc.queryForObject(
                "INSERT INTO servicio (local_id, nombre, duracion_min, precio) VALUES (?, 'Corte', 30, 8000) RETURNING id",
                UUID.class, demoId);
        anaId = jdbc.queryForObject("INSERT INTO profesional (local_id, nombre) VALUES (?, 'Ana') RETURNING id",
                UUID.class, demoId);
        jdbc.update("INSERT INTO profesional_servicio (local_id, profesional_id, servicio_id) VALUES (?, ?, ?)",
                demoId, anaId, corteId);
        jdbc.update("""
                INSERT INTO horario (local_id, profesional_id, dia_semana, hora_inicio, hora_fin) VALUES (?, ?, 2, '10:00', '13:00')
                """, demoId, anaId);
        turno = reservar("10:00", "11 2345-6789");
    }

    @Test
    @DisplayName("HU-22 CA1: un teléfono con turnos recibe un código de 6 dígitos que vence en 10 minutos")
    void pedirCodigo() throws Exception {
        pedir("demo", "11 2345-6789")
                .andExpect(status().isAccepted())
                .andExpect(content().json("{\"vigenciaMinutos\": 10}", true));

        assertThat(enviados.ultimoPara(LUCIA)).hasValueSatisfying(codigo -> assertThat(codigo).matches("\\d{6}"));
        assertThat(jdbc.queryForObject("SELECT vence_en - creado_en FROM codigo_verificacion", String.class))
                .isEqualTo("00:10:00");
    }

    @Test
    @DisplayName("HU-22 CA2: un teléfono sin turnos recibe exactamente la misma respuesta, pero no se le manda nada")
    void telefonoSinTurnos() throws Exception {
        String conTurnos = pedir("demo", "11 2345-6789").andReturn().getResponse().getContentAsString();
        String sinTurnos = pedir("demo", "11 5555-4444")
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();

        assertThat(sinTurnos).isEqualTo(conTurnos);
        assertThat(enviados.ultimoPara("+5491155554444")).isEmpty();
    }

    @Test
    @DisplayName("HU-22 CA3: con el código correcto dentro de los 10 minutos entra y ve sus turnos")
    void verificarYVerTurnos() throws Exception {
        String acceso = entrar("11 2345-6789");

        misTurnos(acceso)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.proximos", hasSize(1)))
                .andExpect(jsonPath("$.proximos[0].turno.id").value(turno.id().toString()))
                .andExpect(jsonPath("$.proximos[0].turno.hora").value("10:00"))
                .andExpect(jsonPath("$.proximos[0].profesionalId").value(anaId.toString()))
                .andExpect(jsonPath("$.proximos[0].servicioIds", contains(corteId.toString())))
                .andExpect(jsonPath("$.historial", hasSize(0)));
    }

    @Test
    @DisplayName("HU-22 CA3: el teléfono se puede escribir de otra forma al verificar")
    void otraFormaDelTelefono() throws Exception {
        pedir("demo", "11 2345-6789");

        verificar("+54 9 11 2345 6789", enviados.ultimoPara(LUCIA).orElseThrow()).andExpect(status().isOk());
    }

    @Test
    @DisplayName("HU-22 CA5: pasados los 10 minutos el código se rechaza")
    void codigoVencido() throws Exception {
        pedir("demo", "11 2345-6789");
        String codigo = enviados.ultimoPara(LUCIA).orElseThrow();
        reloj.fijar(LUNES_9.plus(Duration.ofMinutes(10)));

        verificar("11 2345-6789", codigo)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("CODIGO_INVALIDO"));
    }

    @Test
    @DisplayName("HU-22 CA4: después de 5 intentos fallidos el código queda inválido, aunque después llegue el correcto")
    void cincoIntentos() throws Exception {
        pedir("demo", "11 2345-6789");
        String codigo = enviados.ultimoPara(LUCIA).orElseThrow();
        String incorrecto = codigo.equals("000000") ? "111111" : "000000";

        for (int i = 0; i < CodigoService.MAXIMO_INTENTOS; i++) {
            verificar("11 2345-6789", incorrecto).andExpect(jsonPath("$.codigo").value("CODIGO_INVALIDO"));
        }

        verificar("11 2345-6789", codigo).andExpect(jsonPath("$.codigo").value("CODIGO_INVALIDO"));
        assertThat(jdbc.queryForObject("SELECT intentos FROM codigo_verificacion", Integer.class)).isEqualTo(5);
    }

    @Test
    @DisplayName("HU-22 CA4: con 4 fallos, el correcto todavía entra")
    void cuatroFallosYEntra() throws Exception {
        pedir("demo", "11 2345-6789");
        String codigo = enviados.ultimoPara(LUCIA).orElseThrow();
        String incorrecto = codigo.equals("000000") ? "111111" : "000000";
        for (int i = 0; i < CodigoService.MAXIMO_INTENTOS - 1; i++) {
            verificar("11 2345-6789", incorrecto);
        }

        verificar("11 2345-6789", codigo).andExpect(status().isOk());
    }

    @Test
    @DisplayName("HU-22 CA5: un código ya usado no sirve otra vez")
    void codigoUsado() throws Exception {
        pedir("demo", "11 2345-6789");
        String codigo = enviados.ultimoPara(LUCIA).orElseThrow();
        verificar("11 2345-6789", codigo).andExpect(status().isOk());

        verificar("11 2345-6789", codigo).andExpect(jsonPath("$.codigo").value("CODIGO_INVALIDO"));
    }

    @Test
    @DisplayName("HU-22 CA6: el cuarto pedido de código del mismo teléfono en una hora recibe 429; a la hora, vuelve a poder")
    void tresPorHora() throws Exception {
        for (int i = 0; i < CodigoService.MAXIMO_PEDIDOS_POR_HORA; i++) {
            pedir("demo", "11 2345-6789").andExpect(status().isAccepted());
        }

        pedir("demo", "11 2345-6789")
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
                .andExpect(jsonPath("$.codigo").value("DEMASIADOS_INTENTOS"));
        // Otro teléfono no se ve afectado.
        pedir("demo", "11 5555-4444").andExpect(status().isAccepted());

        reloj.fijar(LUNES_9.plus(Duration.ofHours(1)).plusSeconds(1));
        pedir("demo", "11 2345-6789").andExpect(status().isAccepted());
    }

    @Test
    @DisplayName("HU-22 CA7: en la base no queda el código ni el token del acceso, solo sus hashes")
    void soloHashes() throws Exception {
        pedir("demo", "11 2345-6789");
        String codigo = enviados.ultimoPara(LUCIA).orElseThrow();
        String acceso = JsonPath.read(verificar("11 2345-6789", codigo).andReturn().getResponse().getContentAsString(), "$.token");

        String fila = jdbc.queryForObject("SELECT row_to_json(c)::text FROM codigo_verificacion c", String.class);
        assertThat(fila).doesNotContain("\"" + codigo + "\"");
        assertThat(jdbc.queryForObject("SELECT token_hash FROM acceso_cliente", String.class))
                .isNotEqualTo(acceso).isEqualTo(TokenSecreto.hash(acceso));
    }

    @Test
    @DisplayName("HU-22 CA2: un teléfono sin turnos nunca puede entrar (no hay código que adivinar)")
    void sinTurnosNoEntra() throws Exception {
        pedir("demo", "11 5555-4444");

        verificar("11 5555-4444", "123456").andExpect(jsonPath("$.codigo").value("CODIGO_INVALIDO"));
    }

    @Test
    @DisplayName("HU-22: con el acceso se ve, se cancela y se reprograma un turno, como con el link")
    void accionesConElAcceso() throws Exception {
        String acceso = entrar("11 2345-6789");

        mockMvc.perform(get("/api/publico/turnos/" + turno.id()).header(HttpHeaders.HOST, "demo.localhost")
                        .header(Credencial.HEADER_ACCESO, acceso))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.puedeCambiar").value(true));
        mockMvc.perform(post("/api/publico/turnos/" + turno.id() + "/cancelar").header(HttpHeaders.HOST, "demo.localhost")
                        .header(Credencial.HEADER_ACCESO, acceso))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("cancelado"));
    }

    @Test
    @DisplayName("HU-22: el acceso no sirve para turnos de otro cliente, en otro local ni después de 30 días")
    void limitesDelAcceso() throws Exception {
        Apartado deOtro = reservar("11:00", "11 5555-4444");
        String acceso = entrar("11 2345-6789");

        mockMvc.perform(get("/api/publico/turnos/" + deOtro.id()).header(HttpHeaders.HOST, "demo.localhost")
                        .header(Credencial.HEADER_ACCESO, acceso))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/publico/mis-turnos").header(HttpHeaders.HOST, "otra.localhost")
                        .header(Credencial.HEADER_ACCESO, acceso))
                .andExpect(status().isUnauthorized());

        reloj.fijar(LUNES_9.plus(CodigoService.DURACION_ACCESO));
        misTurnos(acceso)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
    }

    @Test
    @DisplayName("HU-23 CA1: el historial trae los pasados y cancelados, del más nuevo al más viejo; un reprogramado no se repite")
    void historial() throws Exception {
        // De a uno, por el máximo de 2 turnos futuros por teléfono (HU-14).
        Apartado cancelado = reservar("11:00", "11 2345-6789");
        jdbc.update("UPDATE turno SET estado = 'cancelado' WHERE id = ?", cancelado.id());
        Apartado pasado = reservar("12:00", "11 2345-6789");
        jdbc.update("UPDATE turno SET inicio = inicio - interval '7 days', fin = fin - interval '7 days', "
                + "ocupado_hasta = ocupado_hasta - interval '7 days', estado = 'completado' WHERE id = ?", pasado.id());
        String acceso = entrar("11 2345-6789");

        misTurnos(acceso)
                .andExpect(jsonPath("$.proximos[*].turno.id", contains(turno.id().toString())))
                .andExpect(jsonPath("$.historial[*].turno.id", contains(cancelado.id().toString(), pasado.id().toString())));

        // Reprogramar el próximo: aparece el nuevo y el viejo (cancelado por la reprogramación) no.
        String cuerpo = mockMvc.perform(post("/api/publico/turnos/" + turno.id() + "/reprogramar")
                        .header(HttpHeaders.HOST, "demo.localhost").header(Credencial.HEADER_ACCESO, acceso)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"fecha\": \"2026-11-17\", \"hora\": \"12:30\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String nuevo = JsonPath.read(cuerpo, "$.id");
        misTurnos(acceso)
                .andExpect(jsonPath("$.proximos[*].turno.id", contains(nuevo)))
                .andExpect(jsonPath("$.historial", hasSize(2)));
    }

    private String entrar(String telefono) throws Exception {
        pedir("demo", telefono);
        String codigo = enviados.ultimoPara(LUCIA).orElseThrow();
        return JsonPath.read(verificar(telefono, codigo).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$.token");
    }

    private Apartado reservar(String hora, String telefono) {
        Local demo = localResolver.resolver("demo.localhost");
        Apartado apartado = apartados.apartar(demo, new ApartadoService.Pedido(Set.of(corteId), anaId.toString(),
                LocalDate.of(2026, 11, 17), LocalTime.parse(hora), null));
        confirmaciones.confirmar(demo, apartado.id(), apartado.token(),
                new DatosDelCliente("Cliente", telefono, null, "efectivo"));
        return apartado;
    }

    private ResultActions pedir(String subdominio, String telefono) throws Exception {
        return mockMvc.perform(post("/api/publico/codigos")
                .header(HttpHeaders.HOST, subdominio + ".localhost")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"telefono\": \"" + telefono + "\"}"));
    }

    private ResultActions verificar(String telefono, String codigo) throws Exception {
        return mockMvc.perform(post("/api/publico/codigos/verificar")
                .header(HttpHeaders.HOST, "demo.localhost")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"telefono\": \"" + telefono + "\", \"codigo\": \"" + codigo + "\"}"));
    }

    private ResultActions misTurnos(String acceso) throws Exception {
        return mockMvc.perform(get("/api/publico/mis-turnos")
                .header(HttpHeaders.HOST, "demo.localhost")
                .header(Credencial.HEADER_ACCESO, acceso));
    }
}
