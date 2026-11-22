package ar.com.slotly.reserva;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import com.jayway.jsonpath.JsonPath;

import ar.com.slotly.BaseDeTest;
import ar.com.slotly.CodigosEnviadosDeTest;
import ar.com.slotly.MensajesEnviadosDeTest;
import ar.com.slotly.RelojDeTest;
import ar.com.slotly.TestcontainersConfiguration;
import ar.com.slotly.seguridad.IntentosDeIngreso;
import ar.com.slotly.seguridad.LimiteDePedidos;

// La demo publicada (Etapa 9, slotly.demo): la pantalla del código lo muestra, porque quien la prueba no ve el envío
// simulado. Misma configuración que DetrasDelProxyTest (así comparten la app levantada), más slotly.demo.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "slotly.trabajos.activos=false", "server.forward-headers-strategy=native", "slotly.local-por-defecto=demo",
        "slotly.demo=true"})
@Import({TestcontainersConfiguration.class, RelojDeTest.Config.class, CodigosEnviadosDeTest.Config.class,
        MensajesEnviadosDeTest.Config.class})
class DemoPublicadaTest {

    @LocalServerPort
    int puerto;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    LimiteDePedidos limite;

    @Autowired
    IntentosDeIngreso intentos;

    final HttpClient cliente = HttpClient.newHttpClient();

    @BeforeEach
    void preparar() {
        limite.reiniciar();
        intentos.reiniciar();
        BaseDeTest.vaciar(jdbc);
        jdbc.update("INSERT INTO local (nombre, subdominio) VALUES ('Demo', 'demo')");
        jdbc.update("INSERT INTO cliente (local_id, nombre, telefono) SELECT id, 'Lucía', '+5491123456789' FROM local");
    }

    @Test
    @DisplayName("Etapa 9: en la demo, pedir el código lo devuelve, y con él se entra")
    void elCodigoVieneEnLaRespuesta() throws Exception {
        HttpResponse<String> pedido = post("/api/publico/codigos", "{\"telefono\": \"11 2345-6789\"}");
        assertThat(pedido.statusCode()).isEqualTo(202);
        String codigo = JsonPath.read(pedido.body(), "$.codigoDeDemo");
        assertThat(codigo).matches("\\d{6}");

        HttpResponse<String> entrada = post("/api/publico/codigos/verificar",
                "{\"telefono\": \"11 2345-6789\", \"codigo\": \"" + codigo + "\"}");
        assertThat(entrada.statusCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("HU-22 CA2: también en la demo, un teléfono sin turnos recibe la misma forma de respuesta y no entra")
    void telefonoSinTurnos() throws Exception {
        HttpResponse<String> pedido = post("/api/publico/codigos", "{\"telefono\": \"11 5555-4444\"}");
        assertThat(pedido.statusCode()).isEqualTo(202);
        String codigo = JsonPath.read(pedido.body(), "$.codigoDeDemo");
        assertThat(codigo).matches("\\d{6}");

        HttpResponse<String> entrada = post("/api/publico/codigos/verificar",
                "{\"telefono\": \"11 5555-4444\", \"codigo\": \"" + codigo + "\"}");
        assertThat(entrada.statusCode()).isEqualTo(400);
        assertThat(JsonPath.<String>read(entrada.body(), "$.codigo")).isEqualTo("CODIGO_INVALIDO");
    }

    private HttpResponse<String> post(String ruta, String cuerpo) throws Exception {
        HttpRequest pedido = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(cuerpo))
                .build();
        return cliente.send(pedido, HttpResponse.BodyHandlers.ofString());
    }
}
