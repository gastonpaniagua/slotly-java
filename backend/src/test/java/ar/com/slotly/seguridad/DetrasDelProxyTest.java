package ar.com.slotly.seguridad;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import ar.com.slotly.BaseDeTest;
import ar.com.slotly.CodigosEnviadosDeTest;
import ar.com.slotly.MensajesEnviadosDeTest;
import ar.com.slotly.RelojDeTest;
import ar.com.slotly.TestcontainersConfiguration;

// La app detrás del proxy de la plataforma (Etapa 9), con lo mismo que pone el perfil prod: Tomcat de verdad (no
// MockMvc), así se prueba cómo lee X-Forwarded-For y X-Forwarded-Proto. El test le habla desde 127.0.0.1, que Tomcat
// toma como un proxy interno, igual que el de Render. Sin subdominio en el host: usa el local por defecto.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "slotly.trabajos.activos=false", "server.forward-headers-strategy=native", "slotly.local-por-defecto=demo"})
@Import({TestcontainersConfiguration.class, RelojDeTest.Config.class, CodigosEnviadosDeTest.Config.class,
        MensajesEnviadosDeTest.Config.class})
class DetrasDelProxyTest {

    private static final String EMAIL = "duenio@demo.com";
    private static final String CLAVE = "clave";

    @LocalServerPort
    int puerto;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    LimiteDePedidos limite;

    @Autowired
    IntentosDeIngreso intentos;

    @Autowired
    PasswordEncoder encoder;

    final HttpClient cliente = HttpClient.newHttpClient();

    @BeforeEach
    void preparar() {
        limite.reiniciar();
        intentos.reiniciar();
        BaseDeTest.vaciar(jdbc);
        jdbc.update("INSERT INTO local (nombre, subdominio) VALUES ('Demo', 'demo')");
    }

    @Test
    @DisplayName("HU-15: el límite de pedidos cuenta por la IP real del visitante, la que agrega el proxy")
    void limitePorLaIpReal() throws Exception {
        for (int i = 0; i < LimiteDePedidos.MAXIMO_POR_MINUTO; i++) {
            assertThat(apartar("203.0.113.1")).isNotEqualTo(429);
        }

        assertThat(apartar("203.0.113.1")).isEqualTo(429);
        // Otro visitante detrás del mismo proxy no queda frenado.
        assertThat(apartar("203.0.113.2")).isNotEqualTo(429);
        // Inventar una IP en el header no sirve: cuenta la última, la que agregó el proxy.
        assertThat(apartar("198.51.100.7, 203.0.113.1")).isEqualTo(429);
    }

    @Test
    @DisplayName("D1: si el proxy avisa que el pedido llegó por https, la cookie de sesión sale con Secure; si no, sin")
    void cookieSecureConHttps() throws Exception {
        jdbc.update("""
                INSERT INTO usuario (local_id, email, password_hash, rol)
                SELECT id, ?, ?, 'admin' FROM local WHERE subdominio = 'demo'
                """, EMAIL, encoder.encode(CLAVE));

        assertThat(cookieDeSesion(ingresar(Optional.of("https")))).contains("Secure");
        assertThat(cookieDeSesion(ingresar(Optional.empty()))).doesNotContain("Secure");
    }

    private int apartar(String reenviadoPara) throws Exception {
        HttpRequest pedido = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + "/api/publico/apartados"))
                .header("Content-Type", "application/json")
                .header("X-Forwarded-For", reenviadoPara)
                .POST(HttpRequest.BodyPublishers.ofString("{}"))
                .build();
        return cliente.send(pedido, HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    private HttpResponse<Void> ingresar(Optional<String> protocolo) throws Exception {
        HttpRequest.Builder pedido = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + "/api/panel/ingresar"))
                .header("Content-Type", "application/json")
                // CSRF como lo manda el front (ver PanelDeTest.conCsrf).
                .header("Cookie", "XSRF-TOKEN=token-de-prueba")
                .header("X-XSRF-TOKEN", "token-de-prueba")
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"email\": \"" + EMAIL + "\", \"password\": \"" + CLAVE + "\"}"));
        protocolo.ifPresent(p -> pedido.header("X-Forwarded-Proto", p));
        HttpResponse<Void> respuesta = cliente.send(pedido.build(), HttpResponse.BodyHandlers.discarding());
        assertThat(respuesta.statusCode()).isEqualTo(200);
        return respuesta;
    }

    private static String cookieDeSesion(HttpResponse<Void> respuesta) {
        List<String> cookies = respuesta.headers().allValues("Set-Cookie");
        return cookies.stream().filter(c -> c.startsWith("SLOTLY_SESION=")).findFirst()
                .orElseThrow(() -> new AssertionError("sin cookie de sesión: " + cookies));
    }
}
