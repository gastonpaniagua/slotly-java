package ar.com.slotly.panel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import jakarta.servlet.http.Cookie;

import ar.com.slotly.BaseDeTest;
import ar.com.slotly.IntegrationTest;
import ar.com.slotly.RelojDeTest;
import ar.com.slotly.seguridad.IntentosDeIngreso;

// HU-16: entrar y salir del panel, contra la app completa (sesión guardada en el Postgres de Testcontainers).
@IntegrationTest
class IngresoTest {

    private static final Instant AHORA = Instant.parse("2026-11-16T12:00:00Z");
    private static final String CLAVE = "clave-de-ana";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    RelojDeTest reloj;

    @Autowired
    IntentosDeIngreso intentos;

    @Autowired
    PasswordEncoder encoder;

    UUID demoId;
    UUID otraId;
    UUID anaId;

    @BeforeEach
    void preparar() {
        reloj.fijar(AHORA);
        intentos.reiniciar();
        BaseDeTest.vaciar(jdbc);
        demoId = crearLocal("demo");
        otraId = crearLocal("otra");
        anaId = jdbc.queryForObject("INSERT INTO profesional (local_id, nombre) VALUES (?, 'Ana') RETURNING id",
                UUID.class, demoId);
        crearUsuario(demoId, "ana@demo.com", CLAVE, "profesional", anaId, true);
        crearUsuario(demoId, "duenio@demo.com", "clave-del-duenio", "admin", null, true);
    }

    @Test
    @DisplayName("HU-16 CA1: con mail y contraseña correctos entra, y la sesión queda en una cookie httpOnly")
    void entra() throws Exception {
        MvcResult resultado = ingresar("demo", "ana@demo.com", CLAVE)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("ana@demo.com"))
                .andExpect(jsonPath("$.rol").value("profesional"))
                .andExpect(jsonPath("$.profesionalId").value(anaId.toString()))
                .andReturn();

        String setCookie = resultado.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).startsWith(PanelDeTest.COOKIE_SESION + "=").contains("HttpOnly").contains("SameSite=Lax");
        yo("demo", PanelDeTest.sesionDe(resultado))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("ana@demo.com"));
    }

    @Test
    @DisplayName("HU-16: el mail se toma sin importar mayúsculas ni espacios alrededor")
    void mailSinMayusculas() throws Exception {
        ingresar("demo", "  Ana@Demo.COM ", CLAVE).andExpect(status().isOk());
    }

    @Test
    @DisplayName("HU-16 CA2: mail inexistente y contraseña incorrecta dan el mismo 401 con el mismo mensaje")
    void mismoMensaje() throws Exception {
        for (ResultActions intento : new ResultActions[] {
                ingresar("demo", "nadie@demo.com", CLAVE),
                ingresar("demo", "ana@demo.com", "otra-clave") }) {
            intento.andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.codigo").value("CREDENCIALES_INVALIDAS"))
                    .andExpect(jsonPath("$.detail").value("Mail o contraseña incorrectos."))
                    .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
        }
    }

    @Test
    @DisplayName("HU-16 CA2: un usuario desactivado no entra, con el mismo mensaje")
    void usuarioDesactivado() throws Exception {
        jdbc.update("UPDATE usuario SET activo = false WHERE email = 'ana@demo.com'");

        ingresar("demo", "ana@demo.com", CLAVE)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("CREDENCIALES_INVALIDAS"));
    }

    @Test
    @DisplayName("HU-16 CA3: después de 5 fallos seguidos, ese mail recibe 429 durante 15 minutos (aunque la contraseña sea la correcta)")
    void bloqueoPorFallos() throws Exception {
        for (int i = 0; i < IntentosDeIngreso.MAXIMO_FALLOS; i++) {
            ingresar("demo", "ana@demo.com", "mal-" + i).andExpect(status().isUnauthorized());
        }

        ingresar("demo", "ana@demo.com", CLAVE)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "900"))
                .andExpect(jsonPath("$.codigo").value("DEMASIADOS_INTENTOS"));
        // Otro mail no se ve afectado.
        ingresar("demo", "duenio@demo.com", "clave-del-duenio").andExpect(status().isOk());

        reloj.fijar(AHORA.plusSeconds(15 * 60 - 1));
        ingresar("demo", "ana@demo.com", CLAVE).andExpect(status().isTooManyRequests());

        reloj.fijar(AHORA.plusSeconds(15 * 60));
        ingresar("demo", "ana@demo.com", CLAVE).andExpect(status().isOk());
    }

    @Test
    @DisplayName("HU-16 CA3: un ingreso correcto reinicia la cuenta de fallos (son 5 seguidos)")
    void exitoReiniciaLaCuenta() throws Exception {
        for (int i = 0; i < IntentosDeIngreso.MAXIMO_FALLOS - 1; i++) {
            ingresar("demo", "ana@demo.com", "mal").andExpect(status().isUnauthorized());
        }
        ingresar("demo", "ana@demo.com", CLAVE).andExpect(status().isOk());

        ingresar("demo", "ana@demo.com", "mal").andExpect(status().isUnauthorized());
        ingresar("demo", "ana@demo.com", CLAVE).andExpect(status().isOk());
    }

    @Test
    @DisplayName("HU-16 CA4: un usuario del local demo no puede entrar en el subdominio de otra (mismo mensaje genérico)")
    void otroLocalNoEntra() throws Exception {
        ingresar("otra", "ana@demo.com", CLAVE)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("CREDENCIALES_INVALIDAS"));
    }

    @Test
    @DisplayName("HU-16 CA5: una sesión del local demo usada contra la API de otra recibe 401")
    void sesionDeOtroLocal() throws Exception {
        Cookie sesion = PanelDeTest.sesionDe(ingresar("demo", "ana@demo.com", CLAVE).andReturn());

        yo("otra", sesion)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
        yo("demo", sesion).andExpect(status().isOk());
    }

    @Test
    @DisplayName("HU-16 CA6: sin sesión, el panel responde 401; la sesión se guarda con 7 días de inactividad")
    void sinSesionYDuracion() throws Exception {
        mockMvc.perform(get("/api/panel/yo").header(HttpHeaders.HOST, "demo.localhost"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));

        ingresar("demo", "ana@demo.com", CLAVE).andExpect(status().isOk());

        assertThat(jdbc.queryForObject("SELECT max_inactive_interval FROM spring_session", Integer.class))
                .isEqualTo(7 * 24 * 60 * 60);
    }

    @Test
    @DisplayName("HU-16 CA7: al salir, la sesión se borra en el servidor y su cookie ya no sirve en ninguna pestaña")
    void salir() throws Exception {
        Cookie sesion = PanelDeTest.sesionDe(ingresar("demo", "ana@demo.com", CLAVE).andReturn());

        mockMvc.perform(post("/api/panel/salir").header(HttpHeaders.HOST, "demo.localhost").cookie(sesion).with(PanelDeTest.conCsrf()))
                .andExpect(status().isNoContent());

        yo("demo", sesion).andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM spring_session", Integer.class)).isZero();
    }

    @Test
    @DisplayName("HU-16 CA8: las contraseñas se hashean con argon2id")
    void argon2id() {
        assertThat(encoder.encode("cualquier-clave")).startsWith("$argon2id$");
        assertThat(jdbc.queryForList("SELECT password_hash FROM usuario", String.class))
                .allSatisfy(hash -> assertThat(hash).startsWith("$argon2id$"));
    }

    @Test
    @DisplayName("D1: entrar sin el token CSRF se rechaza con 403")
    void ingresarSinCsrf() throws Exception {
        mockMvc.perform(post("/api/panel/ingresar").header(HttpHeaders.HOST, "demo.localhost")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"ana@demo.com\", \"password\": \"" + CLAVE + "\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("SIN_PERMISO"));
    }

    @Test
    @DisplayName("D1: como hace el front, el token CSRF llega en la cookie XSRF-TOKEN y vuelve en el header X-XSRF-TOKEN")
    void csrfComoElFront() throws Exception {
        MvcResult primero = mockMvc.perform(get("/api/panel/yo").header(HttpHeaders.HOST, "demo.localhost")).andReturn();
        Cookie token = primero.getResponse().getCookie("XSRF-TOKEN");
        assertThat(token).isNotNull();
        assertThat(token.isHttpOnly()).isFalse();

        mockMvc.perform(post("/api/panel/ingresar").header(HttpHeaders.HOST, "demo.localhost")
                        .cookie(token).header("X-XSRF-TOKEN", token.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"ana@demo.com\", \"password\": \"" + CLAVE + "\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("HU-18 CA3: un profesional que usa un endpoint de admin recibe 403; el admin pasa")
    void endpointsDeAdmin() throws Exception {
        Cookie profesional = PanelDeTest.sesionDe(ingresar("demo", "ana@demo.com", CLAVE).andReturn());
        Cookie admin = PanelDeTest.sesionDe(ingresar("demo", "duenio@demo.com", "clave-del-duenio").andReturn());

        mockMvc.perform(get("/api/admin/algo").header(HttpHeaders.HOST, "demo.localhost").cookie(profesional))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("SIN_PERMISO"));
        // El admin pasa la seguridad: como el endpoint no existe, 404.
        mockMvc.perform(get("/api/admin/algo").header(HttpHeaders.HOST, "demo.localhost").cookie(admin))
                .andExpect(status().isNotFound());
    }

    private ResultActions ingresar(String subdominio, String email, String password) throws Exception {
        return mockMvc.perform(post("/api/panel/ingresar")
                .header(HttpHeaders.HOST, subdominio + ".localhost")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"" + email + "\", \"password\": \"" + password + "\"}")
                .with(PanelDeTest.conCsrf()));
    }

    private ResultActions yo(String subdominio, Cookie sesion) throws Exception {
        return mockMvc.perform(get("/api/panel/yo").header(HttpHeaders.HOST, subdominio + ".localhost").cookie(sesion));
    }

    private UUID crearLocal(String subdominio) {
        return jdbc.queryForObject("INSERT INTO local (nombre, subdominio) VALUES (?, ?) RETURNING id",
                UUID.class, "Local " + subdominio, subdominio);
    }

    private void crearUsuario(UUID local, String email, String clave, String rol, UUID profesionalId, boolean activo) {
        jdbc.update("""
                INSERT INTO usuario (local_id, email, password_hash, rol, profesional_id, activo) VALUES (?, ?, ?, ?, ?, ?)
                """, local, email, encoder.encode(clave), rol, profesionalId, activo);
    }
}
