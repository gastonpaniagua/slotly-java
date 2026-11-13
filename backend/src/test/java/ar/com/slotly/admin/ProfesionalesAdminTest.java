package ar.com.slotly.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;

import jakarta.servlet.http.Cookie;

import ar.com.slotly.BaseDeTest;
import ar.com.slotly.IntegrationTest;
import ar.com.slotly.RelojDeTest;
import ar.com.slotly.panel.PanelDeTest;
import ar.com.slotly.seguridad.IntentosDeIngreso;

// HU-25 e imágenes (D10): profesionales, sus usuarios y fotos, contra la app completa.
// Reloj: lunes 16/11/2026 a las 9:00 en Buenos Aires.
@IntegrationTest
class ProfesionalesAdminTest {

    private static final String CLAVE = "clave-del-duenio";
    private static final byte[] PNG = conFirma(200, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A);

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
    UUID corteId;
    UUID barbaId;
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
        barbaId = jdbc.queryForObject(
                "INSERT INTO servicio (local_id, nombre, duracion_min, precio) VALUES (?, 'Barba', 20, 5000) RETURNING id",
                UUID.class, demoId);
        jdbc.update("INSERT INTO usuario (local_id, email, password_hash, rol) VALUES (?, 'duenio@demo.com', ?, 'admin')",
                demoId, encoder.encode(CLAVE));
        admin = PanelDeTest.ingresar(mockMvc, "demo", "duenio@demo.com", CLAVE);
    }

    @Test
    @DisplayName("HU-25 CA1: un profesional nuevo con foto aparece en el paso 2 de la reserva para sus servicios")
    void alta() throws Exception {
        String foto = subirImagen(PNG).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String fotoUrl = JsonPath.read(foto, "$.url");

        enviar(post("/api/admin/profesionales"), "{\"nombre\": \"Carla\", \"fotoUrl\": \"" + fotoUrl
                + "\", \"servicioIds\": [\"" + corteId + "\"]}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.activo").value(true))
                .andExpect(jsonPath("$.usuarioEmail").doesNotExist())
                .andExpect(jsonPath("$.servicioIds", contains(corteId.toString())));

        profesionalesPublicos(corteId)
                .andExpect(jsonPath("$[*].nombre", contains("Carla")))
                .andExpect(jsonPath("$[0].fotoUrl").value(fotoUrl));
        profesionalesPublicos(barbaId).andExpect(jsonPath("$[*].nombre", not(hasItem("Carla"))));
    }

    @Test
    @DisplayName("D10: la imagen subida se sirve tal cual, con su tipo y caché larga")
    void imagenSeSirve() throws Exception {
        String url = JsonPath.read(subirImagen(PNG).andReturn().getResponse().getContentAsString(), "$.url");

        mockMvc.perform(get(url).header(HttpHeaders.HOST, "demo.localhost"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(content().bytes(PNG))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, org.hamcrest.Matchers.containsString("immutable")));
        // Desde otro local, no existe.
        jdbc.update("INSERT INTO local (nombre, subdominio) VALUES ('Otra', 'otra')");
        mockMvc.perform(get(url).header(HttpHeaders.HOST, "otra.localhost")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("D10: un archivo que no es PNG, JPG ni WebP (aunque diga serlo) o que pesa más de 500 KB se rechaza")
    void imagenesInvalidas() throws Exception {
        subirImagen("esto no es una imagen".getBytes())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.detail").value("La imagen tiene que ser PNG, JPG o WebP."));
        subirImagen(conFirma(512_001, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("La imagen puede pesar hasta 500 KB."));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM imagen", Integer.class)).isZero();
    }

    @Test
    @DisplayName("HU-25 CA2: con su usuario, el profesional entra al panel y ve solo su agenda")
    void usuarioDelProfesional() throws Exception {
        UUID carla = crearProfesional("Carla");

        enviar(post("/api/admin/profesionales/" + carla + "/usuario"),
                "{\"email\": \" Carla@Demo.com \", \"password\": \"clave-inicial\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.usuarioEmail").value("carla@demo.com"));

        Cookie sesion = PanelDeTest.ingresar(mockMvc, "demo", "carla@demo.com", "clave-inicial");
        mockMvc.perform(get("/api/panel/yo").header(HttpHeaders.HOST, "demo.localhost").cookie(sesion))
                .andExpect(jsonPath("$.rol").value("profesional"))
                .andExpect(jsonPath("$.profesionalId").value(carla.toString()));
        mockMvc.perform(get("/api/panel/agenda").header(HttpHeaders.HOST, "demo.localhost").cookie(sesion)
                        .param("dias", "1").param("profesional", "todos"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("HU-25 CA4: un mail que ya tiene usuario en el local se rechaza, junto al campo del mail")
    void mailRepetido() throws Exception {
        UUID carla = crearProfesional("Carla");

        enviar(post("/api/admin/profesionales/" + carla + "/usuario"),
                "{\"email\": \"duenio@demo.com\", \"password\": \"clave-inicial\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.errores[0].campo").value("email"))
                .andExpect(jsonPath("$.errores[0].mensaje").value("Ese mail ya tiene un usuario en este local."));
    }

    @Test
    @DisplayName("HU-25 CA3: con turnos por delante, la baja pide confirmar; al confirmar, no recibe más reservas y sus turnos quedan")
    void bajaConTurnos() throws Exception {
        UUID carla = crearProfesional("Carla");
        enviar(post("/api/admin/profesionales/" + carla + "/usuario"), "{\"email\": \"carla@demo.com\", \"password\": \"clave-inicial\"}");
        Cookie sesionDeCarla = PanelDeTest.ingresar(mockMvc, "demo", "carla@demo.com", "clave-inicial");
        UUID turno = turnoDe(carla);

        enviar(post("/api/admin/profesionales/" + carla + "/baja"), "{\"confirmar\": false}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("TURNOS_FUTUROS"))
                .andExpect(jsonPath("$.detail").value("Tiene 1 turnos por delante. Confirmá la baja para seguir."));
        mockMvc.perform(get("/api/admin/profesionales/" + carla + "/turnos-futuros")
                        .header(HttpHeaders.HOST, "demo.localhost").cookie(admin))
                .andExpect(jsonPath("$[0].id").value(turno.toString()))
                .andExpect(jsonPath("$[0].inicio").value("2026-11-17T10:00"))
                .andExpect(jsonPath("$[0].clienteNombre").value("Lucía"));

        enviar(post("/api/admin/profesionales/" + carla + "/baja"), "{\"confirmar\": true}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo").value(false));

        profesionalesPublicos(corteId).andExpect(jsonPath("$[*].nombre", not(hasItem("Carla"))));
        assertThat(jdbc.queryForObject("SELECT estado FROM turno WHERE id = ?", String.class, turno)).isEqualTo("confirmado");
        // Su sesión abierta se cerró y ya no puede entrar.
        mockMvc.perform(get("/api/panel/yo").header(HttpHeaders.HOST, "demo.localhost").cookie(sesionDeCarla))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/panel/ingresar").header(HttpHeaders.HOST, "demo.localhost")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"carla@demo.com\", \"password\": \"clave-inicial\"}").with(PanelDeTest.conCsrf()))
                .andExpect(status().isUnauthorized());
        // El admin la sigue viendo en la agenda "Todos", para mover o cancelar ese turno.
        mockMvc.perform(get("/api/panel/agenda").header(HttpHeaders.HOST, "demo.localhost").cookie(admin)
                        .param("desde", "2026-11-17").param("dias", "1"))
                .andExpect(jsonPath("$.profesionales[*].nombre", hasItem("Carla")))
                .andExpect(jsonPath("$.turnos[0].id").value(turno.toString()));
    }

    @Test
    @DisplayName("HU-25: sin turnos por delante, la baja no pide confirmar, y el profesional deja la agenda; el alta lo devuelve")
    void bajaSinTurnosYAlta() throws Exception {
        UUID carla = crearProfesional("Carla");

        enviar(post("/api/admin/profesionales/" + carla + "/baja"), "{\"confirmar\": false}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo").value(false));
        mockMvc.perform(get("/api/panel/agenda").header(HttpHeaders.HOST, "demo.localhost").cookie(admin)
                        .param("desde", "2026-11-17").param("dias", "1"))
                .andExpect(jsonPath("$.profesionales[*].nombre", not(hasItem("Carla"))));

        enviar(post("/api/admin/profesionales/" + carla + "/alta"), "")
                .andExpect(jsonPath("$.activo").value(true));
        profesionalesPublicos(corteId).andExpect(jsonPath("$[*].nombre", hasItem("Carla")));
    }

    @Test
    @DisplayName("HU-25: editar cambia los servicios; un servicio de otro local o una foto ajena se rechazan")
    void editar() throws Exception {
        UUID carla = crearProfesional("Carla");
        UUID otraId = jdbc.queryForObject("INSERT INTO local (nombre, subdominio) VALUES ('Otra', 'otra') RETURNING id", UUID.class);
        UUID ajeno = jdbc.queryForObject(
                "INSERT INTO servicio (local_id, nombre, duracion_min, precio) VALUES (?, 'Ajeno', 30, 1) RETURNING id",
                UUID.class, otraId);

        enviar(put("/api/admin/profesionales/" + carla), "{\"nombre\": \"Carla P.\", \"fotoUrl\": null, \"servicioIds\": [\"" + barbaId + "\"]}")
                .andExpect(jsonPath("$.nombre").value("Carla P."))
                .andExpect(jsonPath("$.servicioIds", contains(barbaId.toString())));
        enviar(put("/api/admin/profesionales/" + carla), "{\"nombre\": \"Carla\", \"fotoUrl\": null, \"servicioIds\": [\"" + ajeno + "\"]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("servicioIds"));
        enviar(put("/api/admin/profesionales/" + carla), "{\"nombre\": \"Carla\", \"fotoUrl\": \"https://otro.sitio/foto.png\", \"servicioIds\": []}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("fotoUrl"));
    }

    @Test
    @DisplayName("HU-25: un profesional no puede administrar profesionales ni subir imágenes (403)")
    void profesionalNo() throws Exception {
        UUID carla = crearProfesional("Carla");
        enviar(post("/api/admin/profesionales/" + carla + "/usuario"), "{\"email\": \"carla@demo.com\", \"password\": \"clave-inicial\"}");
        Cookie sesion = PanelDeTest.ingresar(mockMvc, "demo", "carla@demo.com", "clave-inicial");

        mockMvc.perform(get("/api/admin/profesionales").header(HttpHeaders.HOST, "demo.localhost").cookie(sesion))
                .andExpect(status().isForbidden());
        mockMvc.perform(multipart("/api/admin/imagenes").file(new MockMultipartFile("archivo", "f.png", "image/png", PNG))
                        .header(HttpHeaders.HOST, "demo.localhost").cookie(sesion).with(PanelDeTest.conCsrf()))
                .andExpect(status().isForbidden());
    }

    private UUID crearProfesional(String nombre) throws Exception {
        MvcResult resultado = enviar(post("/api/admin/profesionales"),
                "{\"nombre\": \"" + nombre + "\", \"fotoUrl\": null, \"servicioIds\": [\"" + corteId + "\"]}")
                .andExpect(status().isCreated()).andReturn();
        return UUID.fromString(JsonPath.read(resultado.getResponse().getContentAsString(), "$.id"));
    }

    private UUID turnoDe(UUID profesional) {
        UUID cliente = jdbc.queryForObject("INSERT INTO cliente (local_id, nombre) VALUES (?, 'Lucía') RETURNING id",
                UUID.class, demoId);
        return jdbc.queryForObject("""
                INSERT INTO turno (local_id, profesional_id, cliente_id, tipo, estado, inicio, fin, ocupado_hasta)
                VALUES (?, ?, ?, 'manual', 'confirmado', '2026-11-17T10:00-03:00', '2026-11-17T10:30-03:00', '2026-11-17T10:30-03:00')
                RETURNING id
                """, UUID.class, demoId, profesional, cliente);
    }

    private ResultActions subirImagen(byte[] datos) throws Exception {
        return mockMvc.perform(multipart("/api/admin/imagenes")
                .file(new MockMultipartFile("archivo", "foto.png", "image/png", datos))
                .header(HttpHeaders.HOST, "demo.localhost").cookie(admin).with(PanelDeTest.conCsrf()));
    }

    private ResultActions enviar(MockHttpServletRequestBuilder pedido, String cuerpo) throws Exception {
        if (!cuerpo.isEmpty()) {
            pedido.contentType(MediaType.APPLICATION_JSON).content(cuerpo);
        }
        return mockMvc.perform(pedido.header(HttpHeaders.HOST, "demo.localhost").cookie(admin).with(PanelDeTest.conCsrf()));
    }

    private ResultActions profesionalesPublicos(UUID servicio) throws Exception {
        return mockMvc.perform(get("/api/publico/profesionales").header(HttpHeaders.HOST, "demo.localhost")
                .param("servicios", servicio.toString()));
    }

    // Un "archivo" de ese tamaño que empieza con la firma dada (lo que mira la API para saber el tipo).
    private static byte[] conFirma(int tamanio, int... firma) {
        byte[] datos = new byte[tamanio];
        Arrays.fill(datos, (byte) 7);
        for (int i = 0; i < firma.length; i++) {
            datos[i] = (byte) firma[i];
        }
        return datos;
    }
}
