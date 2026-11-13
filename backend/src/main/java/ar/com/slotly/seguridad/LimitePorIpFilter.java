package ar.com.slotly.seguridad;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import ar.com.slotly.error.SlotlyException;

// Frena reservas automáticas (HU-15): más de 10 pedidos por minuto de la misma IP para apartar o confirmar reciben
// 429 y no llegan al controlador, así no se crea nada. Ver la disponibilidad (GET) no cuenta.
// El 429 sale por el ApiExceptionHandler, con el mismo ProblemDetail y "codigo" que el resto.
// En producción, detrás de un proxy, la IP real llega en X-Forwarded-For: se configura en el deploy (Etapa 9).
// Se registra en la Config de abajo y no con @Component: así los @WebMvcTest (que no cargan el reloj) no lo levantan.
class LimitePorIpFilter extends OncePerRequestFilter {

    private final LimiteDePedidos limite;
    private final HandlerExceptionResolver resolver;

    LimitePorIpFilter(LimiteDePedidos limite, HandlerExceptionResolver resolver) {
        this.limite = limite;
        this.resolver = resolver;
    }

    @Configuration
    static class Config {

        @Bean
        FilterRegistrationBean<LimitePorIpFilter> limitePorIp(LimiteDePedidos limite,
                @Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) {
            FilterRegistrationBean<LimitePorIpFilter> registro =
                    new FilterRegistrationBean<>(new LimitePorIpFilter(limite, resolver));
            registro.addUrlPatterns("/api/publico/apartados", "/api/publico/apartados/*",
                    "/api/publico/codigos", "/api/publico/codigos/*");
            return registro;
        }
    }

    // Solo los POST de reserva (apartar y confirmar) y los de pedir y verificar códigos (HU-22): así nadie prueba
    // teléfonos o códigos en masa desde una IP.
    @Override
    protected boolean shouldNotFilter(HttpServletRequest pedido) {
        String ruta = pedido.getRequestURI();
        return !"POST".equals(pedido.getMethod())
                || !(ruta.startsWith("/api/publico/apartados") || ruta.startsWith("/api/publico/codigos"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest pedido, HttpServletResponse respuesta, FilterChain cadena)
            throws ServletException, IOException {
        if (!limite.permitir(pedido.getRemoteAddr())) {
            respuesta.setHeader("Retry-After", String.valueOf(limite.segundosHastaElProximoMinuto()));
            resolver.resolveException(pedido, respuesta, null, new SlotlyException(HttpStatus.TOO_MANY_REQUESTS,
                    "DEMASIADOS_INTENTOS", "Demasiados intentos, esperá un minuto."));
            return;
        }
        cadena.doFilter(pedido, respuesta);
    }
}
