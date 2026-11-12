package ar.com.slotly.reserva;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
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
import org.springframework.test.web.servlet.ResultActions;

import ar.com.slotly.BaseDeTest;
import ar.com.slotly.IntegrationTest;
import ar.com.slotly.RelojDeTest;
import ar.com.slotly.error.SlotlyException;
import ar.com.slotly.local.Local;
import ar.com.slotly.local.LocalResolver;
import ar.com.slotly.seguridad.LimiteDePedidos;
import ar.com.slotly.reserva.ApartadoService.Apartado;
import ar.com.slotly.reserva.ConfirmacionService.DatosDelCliente;

// Confirmar la reserva y ver el turno, contra Postgres real y con el reloj fijo (lunes 16/11/2026 9:00).
// Cada local de prueba tiene Corte (30 min, $ 8.000), Barba (30 min, $ 5.000) y a Ana, que hace los dos y trabaja
// los martes de 10 a 13. Máximo 2 turnos futuros por teléfono (el valor por defecto).
@IntegrationTest
class ConfirmacionTest {

    private static final LocalDate MARTES = LocalDate.of(2026, 11, 17);
    private static final Instant LUNES_9 = Instant.parse("2026-11-16T12:00:00Z");
    private static final String CELULAR = "11 2345-6789";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    RelojDeTest reloj;

    @Autowired
    ApartadoService apartados;

    @Autowired
    ConfirmacionService confirmaciones;

    @Autowired
    LocalResolver localResolver;

    LocalDePrueba demo;

    record LocalDePrueba(UUID id, String host, UUID corte, UUID barba, UUID ana) {
    }

    @Autowired
    LimiteDePedidos limite;

    @BeforeEach
    void prepararBase() {
        reloj.fijar(LUNES_9);
        limite.reiniciar();   // cada test arranca sin pedidos contados (HU-15)
        BaseDeTest.vaciar(jdbc);
        demo = crearLocal("demo");
        jdbc.update("UPDATE local SET alias = 'barberia.demo', cbu = '0000003100000000000001', titular_cuenta = 'Juan Pérez'");
    }

    @Test
    @DisplayName("HU-10 CA1: al confirmar devuelve día, hora, profesional, servicios, total y forma de pago")
    void confirmaLaReserva() throws Exception {
        Apartado apartado = apartar(demo, "10:00", demo.corte(), demo.barba());

        confirmar(demo, apartado, CELULAR, "efectivo")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(apartado.id().toString()))
                .andExpect(jsonPath("$.estado").value("confirmado"))
                .andExpect(jsonPath("$.fecha").value("2026-11-17"))
                .andExpect(jsonPath("$.hora").value("10:00"))
                .andExpect(jsonPath("$.profesionalNombre").value("Ana"))
                .andExpect(jsonPath("$.servicios[*].nombre", containsInAnyOrder("Corte", "Barba")))
                .andExpect(jsonPath("$.duracionMin").value(60))
                .andExpect(jsonPath("$.precioTotal").value(13000))
                .andExpect(jsonPath("$.formaPago").value("efectivo"))
                .andExpect(jsonPath("$.transferencia").doesNotExist());

        Map<String, Object> cliente = jdbc.queryForMap("SELECT nombre, telefono, consentimiento_en FROM cliente");
        assertThat(cliente.get("nombre")).isEqualTo("Lucía");
        assertThat(cliente.get("telefono")).isEqualTo("+5491123456789");
        assertThat(cliente.get("consentimiento_en")).isNotNull();
    }

    @Test
    @DisplayName("HU-10 CA3: el mismo celular escrito de dos formas es el mismo cliente del local")
    void mismoClienteConDistintasFormas() throws Exception {
        confirmar(demo, apartar(demo, "10:00", demo.corte()), "11 2345-6789", "efectivo").andExpect(status().isOk());
        confirmar(demo, apartar(demo, "11:00", demo.corte()), "+54 9 11 2345 6789", "efectivo").andExpect(status().isOk());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM cliente", Integer.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("HU-10 CA2 y CA4: datos inválidos o sin consentimiento responden 400 con el error de cada campo")
    void datosInvalidos() throws Exception {
        Apartado apartado = apartar(demo, "10:00", demo.corte());
        String cuerpo = """
                {"token": "%s", "nombre": "", "telefono": "123", "email": "ana@", "formaPago": "tarjeta", "consentimiento": false}
                """.formatted(apartado.token());

        enviar(demo, "/api/publico/apartados/" + apartado.id() + "/confirmar", cuerpo)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.errores[*].campo",
                        containsInAnyOrder("nombre", "telefono", "email", "formaPago", "consentimiento")));
        // Un nombre de más de 80 caracteres también.
        enviar(demo, "/api/publico/apartados/" + apartado.id() + "/confirmar", """
                {"token": "%s", "nombre": "%s", "telefono": "%s", "formaPago": "efectivo", "consentimiento": true}
                """.formatted(apartado.token(), "a".repeat(81), CELULAR))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("nombre"));
        // Nada se confirmó.
        assertThat(jdbc.queryForObject("SELECT estado FROM turno", String.class)).isEqualTo("apartado");
    }

    @Test
    @DisplayName("HU-10 CA5: con transferencia, la respuesta trae alias, CBU y titular del local")
    void datosDeTransferencia() throws Exception {
        confirmar(demo, apartar(demo, "10:00", demo.corte()), CELULAR, "transferencia")
                .andExpect(jsonPath("$.formaPago").value("transferencia"))
                .andExpect(jsonPath("$.transferencia.alias").value("barberia.demo"))
                .andExpect(jsonPath("$.transferencia.cbu").value("0000003100000000000001"))
                .andExpect(jsonPath("$.transferencia.titular").value("Juan Pérez"));
    }

    @Test
    @DisplayName("HU-10 CA6: confirmar dos veces el mismo apartado crea un solo turno y responde lo mismo")
    void confirmarDosVeces() throws Exception {
        Apartado apartado = apartar(demo, "10:00", demo.corte());

        confirmar(demo, apartado, CELULAR, "efectivo").andExpect(status().isOk());
        confirmar(demo, apartado, CELULAR, "efectivo")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(apartado.id().toString()));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM turno WHERE estado = 'confirmado'", Integer.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("HU-10 CA7 y CA9: con el token se ve el mismo turno; con otro token o sin token, no")
    void verElTurnoConSuToken() throws Exception {
        Apartado apartado = apartar(demo, "10:00", demo.corte());
        confirmar(demo, apartado, CELULAR, "efectivo");

        verTurno(demo, apartado.id(), apartado.token())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("confirmado"))
                .andExpect(jsonPath("$.hora").value("10:00"));
        verTurno(demo, apartado.id(), "inventado")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("TURNO_NO_ENCONTRADO"));
        mockMvc.perform(get("/api/publico/turnos/" + apartado.id()).header(HttpHeaders.HOST, demo.host()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("HU-19 CA1 y HU-20 CA2: Mi turno dice si el cliente todavía puede cambiarlo solo (más de 3 horas antes)")
    void puedeCambiarHastaTresHorasAntes() throws Exception {
        Apartado apartado = apartar(demo, "10:00", demo.corte());   // martes 10:00 en Buenos Aires = 13:00 UTC
        confirmar(demo, apartado, CELULAR, "efectivo");

        verTurno(demo, apartado.id(), apartado.token())
                .andExpect(jsonPath("$.puedeCambiar").value(true))
                .andExpect(jsonPath("$.yaPaso").value(false));

        reloj.fijar(Instant.parse("2026-11-17T09:59:00Z"));   // 6:59: faltan 3 h 1 min
        verTurno(demo, apartado.id(), apartado.token()).andExpect(jsonPath("$.puedeCambiar").value(true));

        reloj.fijar(Instant.parse("2026-11-17T10:00:00Z"));   // 7:00: faltan justo 3 horas
        verTurno(demo, apartado.id(), apartado.token())
                .andExpect(jsonPath("$.puedeCambiar").value(false))
                .andExpect(jsonPath("$.yaPaso").value(false));

        reloj.fijar(Instant.parse("2026-11-17T13:00:00Z"));   // 10:00: empezó
        verTurno(demo, apartado.id(), apartado.token())
                .andExpect(jsonPath("$.puedeCambiar").value(false))
                .andExpect(jsonPath("$.yaPaso").value(true));
    }

    @Test
    @DisplayName("HU-19: un turno cancelado ya no se puede cambiar")
    void canceladoNoSeCambia() throws Exception {
        Apartado apartado = apartar(demo, "10:00", demo.corte());
        confirmar(demo, apartado, CELULAR, "efectivo");
        jdbc.update("UPDATE turno SET estado = 'cancelado' WHERE id = ?", apartado.id());

        verTurno(demo, apartado.id(), apartado.token())
                .andExpect(jsonPath("$.estado").value("cancelado"))
                .andExpect(jsonPath("$.puedeCambiar").value(false));
    }

    @Test
    @DisplayName("HU-19 CA3: 30 días después del turno el link vence: 410 LINK_VENCIDO (distinto de un link inválido)")
    void linkVencido() throws Exception {
        Apartado apartado = apartar(demo, "10:00", demo.corte());
        confirmar(demo, apartado, CELULAR, "efectivo");
        Instant vence = jdbc.queryForObject("SELECT token_vence_en FROM turno WHERE id = ?", Timestamp.class,
                apartado.id()).toInstant();
        assertThat(vence).isEqualTo(Instant.parse("2026-11-17T13:30:00Z").plus(Duration.ofDays(30)));

        reloj.fijar(vence.minusSeconds(1));
        verTurno(demo, apartado.id(), apartado.token()).andExpect(status().isOk());

        reloj.fijar(vence);
        verTurno(demo, apartado.id(), apartado.token())
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.codigo").value("LINK_VENCIDO"));
        // Con un token inventado sigue siendo "no encontrado": el vencimiento solo lo ve quien tiene el link.
        verTurno(demo, apartado.id(), "inventado").andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("HU-19 CA5: el link de un turno del local demo, abierto en el subdominio de otro local, no encuentra nada")
    void linkDeOtroLocal() throws Exception {
        LocalDePrueba otra = crearLocal("otra");
        Apartado apartado = apartar(demo, "10:00", demo.corte());
        confirmar(demo, apartado, CELULAR, "efectivo");

        verTurno(otra, apartado.id(), apartado.token())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("TURNO_NO_ENCONTRADO"));
    }

    @Test
    @DisplayName("HU-19 CA4: en la base solo está el hash del token, nunca el token")
    void soloElHash() throws Exception {
        Apartado apartado = apartar(demo, "10:00", demo.corte());
        confirmar(demo, apartado, CELULAR, "efectivo");

        String guardado = jdbc.queryForObject("SELECT token_hash FROM turno WHERE id = ?", String.class, apartado.id());
        assertThat(guardado).isNotEqualTo(apartado.token()).isEqualTo(TokenSecreto.hash(apartado.token()));
    }

    @Test
    @DisplayName("Con un token que no corresponde no se puede confirmar el apartado de otro")
    void confirmarConOtroToken() throws Exception {
        Apartado apartado = apartar(demo, "10:00", demo.corte());
        Apartado falso = new Apartado(apartado.id(), "inventado", null, 0, null, null, null, null, 0, 0, null);

        confirmar(demo, falso, CELULAR, "efectivo")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("TURNO_NO_ENCONTRADO"));
    }

    @Test
    @DisplayName("HU-11 CA2: pasados los 5 minutos ya no se puede confirmar: APARTADO_VENCIDO")
    void apartadoVencido() throws Exception {
        Apartado apartado = apartar(demo, "10:00", demo.corte());
        reloj.fijar(LUNES_9.plus(Duration.ofMinutes(6)));

        confirmar(demo, apartado, CELULAR, "efectivo")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("APARTADO_VENCIDO"))
                .andExpect(jsonPath("$.detail").value("Se venció el tiempo para confirmar. Elegí la hora de nuevo."));
    }

    @Test
    @DisplayName("HU-14 CA1: con 2 turnos futuros, el tercero se rechaza con MAXIMO_TURNOS y no se crea")
    void maximoDeTurnosFuturos() throws Exception {
        confirmar(demo, apartar(demo, "10:00", demo.corte()), CELULAR, "efectivo").andExpect(status().isOk());
        confirmar(demo, apartar(demo, "11:00", demo.corte()), CELULAR, "efectivo").andExpect(status().isOk());

        confirmar(demo, apartar(demo, "12:00", demo.corte()), CELULAR, "efectivo")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("MAXIMO_TURNOS"))
                .andExpect(jsonPath("$.detail").value("Ya tenés 2 turnos reservados. Podés verlos en \"Mi turno\"."));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM turno WHERE estado = 'confirmado'", Integer.class)).isEqualTo(2);
    }

    @Test
    @DisplayName("HU-14 CA2: si uno de los 2 turnos ya pasó, se puede reservar otro (solo cuentan los futuros)")
    void unoYaPaso() throws Exception {
        confirmar(demo, apartar(demo, "10:00", demo.corte()), CELULAR, "efectivo");
        confirmar(demo, apartar(demo, "11:00", demo.corte()), CELULAR, "efectivo");

        reloj.fijar(Instant.parse("2026-11-17T13:15:00Z"));   // martes 10:15: el de las 10:00 ya empezó

        confirmar(demo, apartar(demo, "12:00", demo.corte()), CELULAR, "efectivo").andExpect(status().isOk());
    }

    @Test
    @DisplayName("HU-14 CA3: si uno de los 2 turnos está cancelado, se puede reservar otro")
    void unoCancelado() throws Exception {
        Apartado primero = apartar(demo, "10:00", demo.corte());
        confirmar(demo, primero, CELULAR, "efectivo");
        confirmar(demo, apartar(demo, "11:00", demo.corte()), CELULAR, "efectivo");
        jdbc.update("UPDATE turno SET estado = 'cancelado' WHERE id = ?", primero.id());

        confirmar(demo, apartar(demo, "12:00", demo.corte()), CELULAR, "efectivo").andExpect(status().isOk());
    }

    @Test
    @DisplayName("HU-14 CA4: con 2 turnos futuros en un local, se puede reservar en otro (el límite es por local)")
    void otroLocal() throws Exception {
        confirmar(demo, apartar(demo, "10:00", demo.corte()), CELULAR, "efectivo");
        confirmar(demo, apartar(demo, "11:00", demo.corte()), CELULAR, "efectivo");
        LocalDePrueba otra = crearLocal("otra");

        confirmar(otra, apartar(otra, "10:00", otra.corte()), CELULAR, "efectivo").andExpect(status().isOk());
    }

    @Test
    @DisplayName("HU-14 CA5: con 1 turno futuro y 2 confirmaciones a la vez del mismo teléfono, entra solo una")
    void dosConfirmacionesALaVez() throws Exception {
        confirmar(demo, apartar(demo, "10:00", demo.corte()), CELULAR, "efectivo").andExpect(status().isOk());
        Apartado segundo = apartar(demo, "11:00", demo.corte());
        Apartado tercero = apartar(demo, "12:00", demo.corte());
        Local local = localResolver.resolver(demo.host());
        DatosDelCliente datos = new DatosDelCliente("Lucía", CELULAR, null, "efectivo");

        CountDownLatch largada = new CountDownLatch(1);
        ExecutorService hilos = Executors.newFixedThreadPool(2);
        try {
            List<Future<String>> resultados = new ArrayList<>();
            for (Apartado apartado : List.of(segundo, tercero)) {
                resultados.add(hilos.submit(() -> {
                    largada.await();
                    try {
                        confirmaciones.confirmar(local, apartado.id(), apartado.token(), datos);
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

            assertThat(respuestas).containsExactlyInAnyOrder("OK", "MAXIMO_TURNOS");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM turno WHERE estado = 'confirmado'", Integer.class))
                    .isEqualTo(2);
        } finally {
            hilos.shutdownNow();
        }
    }

    // ---- Ayudas ----

    private Apartado apartar(LocalDePrueba local, String hora, UUID... servicios) {
        return apartados.apartar(localResolver.resolver(local.host()), new ApartadoService.Pedido(
                Set.of(servicios), local.ana().toString(), MARTES, LocalTime.parse(hora), null));
    }

    private ResultActions confirmar(LocalDePrueba local, Apartado apartado, String telefono, String formaPago)
            throws Exception {
        return enviar(local, "/api/publico/apartados/" + apartado.id() + "/confirmar", """
                {"token": "%s", "nombre": "Lucía", "telefono": "%s", "email": "lucia@mail.com",
                 "formaPago": "%s", "consentimiento": true}
                """.formatted(apartado.token(), telefono, formaPago));
    }

    private ResultActions verTurno(LocalDePrueba local, UUID id, String token) throws Exception {
        return mockMvc.perform(get("/api/publico/turnos/" + id)
                .header(HttpHeaders.HOST, local.host())
                .header(ConfirmacionController.HEADER_TOKEN, token));
    }

    private ResultActions enviar(LocalDePrueba local, String url, String cuerpo) throws Exception {
        return mockMvc.perform(post(url)
                .header(HttpHeaders.HOST, local.host())
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo));
    }

    // Un local con Corte, Barba y Ana (que hace los dos y trabaja los martes de 10 a 13).
    private LocalDePrueba crearLocal(String subdominio) {
        UUID id = jdbc.queryForObject("INSERT INTO local (nombre, subdominio) VALUES (?, ?) RETURNING id",
                UUID.class, "Local " + subdominio, subdominio);
        UUID corte = jdbc.queryForObject("""
                INSERT INTO servicio (local_id, nombre, duracion_min, precio, orden) VALUES (?, 'Corte', 30, 8000, 1) RETURNING id
                """, UUID.class, id);
        UUID barba = jdbc.queryForObject("""
                INSERT INTO servicio (local_id, nombre, duracion_min, precio, orden) VALUES (?, 'Barba', 30, 5000, 2) RETURNING id
                """, UUID.class, id);
        UUID ana = jdbc.queryForObject("INSERT INTO profesional (local_id, nombre) VALUES (?, 'Ana') RETURNING id",
                UUID.class, id);
        for (UUID servicio : List.of(corte, barba)) {
            jdbc.update("INSERT INTO profesional_servicio (local_id, profesional_id, servicio_id) VALUES (?, ?, ?)",
                    id, ana, servicio);
        }
        jdbc.update("""
                INSERT INTO horario (local_id, profesional_id, dia_semana, hora_inicio, hora_fin) VALUES (?, ?, 2, '10:00', '13:00')
                """, id, ana);
        return new LocalDePrueba(id, subdominio + ".localhost", corte, barba, ana);
    }
}
