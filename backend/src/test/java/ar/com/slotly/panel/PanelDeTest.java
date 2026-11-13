package ar.com.slotly.panel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.Arrays;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import jakarta.servlet.http.Cookie;

// Ayudas para los tests del panel: entrar como lo hace el front y mandar el token CSRF.
public final class PanelDeTest {

    public static final String COOKIE_SESION = "SLOTLY_SESION";

    private PanelDeTest() {
    }

    // El token CSRF como lo manda el front: en la cookie XSRF-TOKEN y repetido en el header X-XSRF-TOKEN.
    // (No se usa el csrf() de spring-security-test: guarda el token en una sesión que la app real no crea.)
    public static RequestPostProcessor conCsrf() {
        return pedido -> {
            Cookie[] actuales = pedido.getCookies() == null ? new Cookie[0] : pedido.getCookies();
            Cookie[] todas = Arrays.copyOf(actuales, actuales.length + 1);
            todas[actuales.length] = new Cookie("XSRF-TOKEN", "token-de-prueba");
            pedido.setCookies(todas);
            pedido.addHeader("X-XSRF-TOKEN", "token-de-prueba");
            return pedido;
        };
    }

    // Inicia sesión en el subdominio y devuelve la cookie de la sesión.
    public static Cookie ingresar(MockMvc mockMvc, String subdominio, String email, String password) throws Exception {
        MvcResult resultado = mockMvc.perform(post("/api/panel/ingresar")
                        .header(HttpHeaders.HOST, subdominio + ".localhost")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"" + email + "\", \"password\": \"" + password + "\"}")
                        .with(conCsrf()))
                .andReturn();
        return sesionDe(resultado);
    }

    public static Cookie sesionDe(MvcResult resultado) {
        Cookie cookie = resultado.getResponse().getCookie(COOKIE_SESION);
        assertThat(cookie).as("cookie de sesión").isNotNull();
        return cookie;
    }
}
