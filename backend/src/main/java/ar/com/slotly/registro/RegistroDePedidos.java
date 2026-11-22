package ar.com.slotly.registro;

import java.io.IOException;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

// Logs (Etapa 9): cada pedido lleva un id, y todo lo que se registra mientras se atiende sale con ese id y el local
// (el host). Si hay un problema, con el id se encuentra todo lo que pasó en ese pedido; el id vuelve en el header
// X-Request-Id, así quien reporta un error puede pasarlo. El usuario del panel lo agrega SesionDelLocalFilter.
// Al terminar, una línea por pedido a la API: método, ruta, estado y cuánto tardó (sin datos del cuerpo).
// Corre primero que todo (también antes de Spring Security), así los 401 y 403 también quedan registrados.
public class RegistroDePedidos extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";
    // Las claves del MDC: van en cada línea del log mientras dura el pedido.
    public static final String PEDIDO = "pedido";
    public static final String LOCAL = "local";
    public static final String USUARIO = "usuario";

    private static final Logger LOG = LoggerFactory.getLogger(RegistroDePedidos.class);
    // Un id que manda el proxy se reusa solo si es razonable: nada que pueda ensuciar el log.
    private static final Pattern ID_VALIDO = Pattern.compile("[A-Za-z0-9-]{8,64}");

    @Configuration
    static class Config {

        @Bean
        FilterRegistrationBean<RegistroDePedidos> registroDePedidos() {
            FilterRegistrationBean<RegistroDePedidos> registro = new FilterRegistrationBean<>(new RegistroDePedidos());
            registro.setOrder(Ordered.HIGHEST_PRECEDENCE);
            return registro;
        }
    }

    @Override
    protected void doFilterInternal(HttpServletRequest pedido, HttpServletResponse respuesta, FilterChain cadena)
            throws ServletException, IOException {
        long inicio = System.nanoTime();
        String id = idDelPedido(pedido.getHeader(HEADER));
        MDC.put(PEDIDO, id);
        MDC.put(LOCAL, host(pedido.getHeader(HttpHeaders.HOST)));
        respuesta.setHeader(HEADER, id);
        try {
            cadena.doFilter(pedido, respuesta);
        } finally {
            if (seRegistra(pedido.getRequestURI())) {
                LOG.info("{} {} → {} en {} ms", pedido.getMethod(), pedido.getRequestURI(), respuesta.getStatus(),
                        (System.nanoTime() - inicio) / 1_000_000);
            }
            MDC.clear();
        }
    }

    // Solo la API: los archivos del front no aportan, y el health lo pide la plataforma cada pocos segundos.
    private static boolean seRegistra(String ruta) {
        return ruta.startsWith("/api/") && !ruta.equals("/api/health");
    }

    private static String idDelPedido(String recibido) {
        return recibido != null && ID_VALIDO.matcher(recibido).matches()
                ? recibido
                : UUID.randomUUID().toString().substring(0, 8);
    }

    private static String host(String host) {
        return host == null ? "" : host.toLowerCase(Locale.ROOT).split(":")[0];
    }
}
