package ar.com.slotly.seguridad;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import ar.com.slotly.error.SlotlyException;
import ar.com.slotly.local.Local;
import ar.com.slotly.local.LocalResolver;
import ar.com.slotly.usuario.UsuarioSesion;

// Una sesión vale solo en el local donde se inició (HU-16 CA5). El navegador ya no manda la cookie de
// demo.slotly.com.ar a otra.slotly.com.ar, pero la API no se fía: compara el local de la sesión con el del Host
// en cada pedido del panel. Si no coinciden, 401, como si no hubiera sesión.
// Corre después de Spring Security (ya sabe quién es el usuario) y antes de los controladores, así ningún
// endpoint del panel se puede olvidar de chequearlo. Se registra como LimitePorIpFilter, por la misma razón.
class SesionDelLocalFilter extends OncePerRequestFilter {

    private final LocalResolver localResolver;
    private final HandlerExceptionResolver resolver;

    SesionDelLocalFilter(LocalResolver localResolver, HandlerExceptionResolver resolver) {
        this.localResolver = localResolver;
        this.resolver = resolver;
    }

    @Configuration
    static class Config {

        @Bean
        FilterRegistrationBean<SesionDelLocalFilter> sesionDelLocal(LocalResolver localResolver,
                @Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) {
            FilterRegistrationBean<SesionDelLocalFilter> registro =
                    new FilterRegistrationBean<>(new SesionDelLocalFilter(localResolver, resolver));
            registro.addUrlPatterns("/api/panel/*", "/api/admin/*");
            return registro;
        }
    }

    // Entrar y salir no dependen de una sesión previa.
    @Override
    protected boolean shouldNotFilter(HttpServletRequest pedido) {
        String ruta = pedido.getRequestURI();
        return ruta.equals("/api/panel/ingresar") || ruta.equals("/api/panel/salir");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest pedido, HttpServletResponse respuesta, FilterChain cadena)
            throws ServletException, IOException {
        Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacion != null && autenticacion.getPrincipal() instanceof UsuarioSesion usuario) {
            try {
                Local local = localResolver.resolver(pedido.getHeader(HttpHeaders.HOST));
                if (!local.getId().equals(usuario.localId())) {
                    throw new SlotlyException(HttpStatus.UNAUTHORIZED, "NO_AUTENTICADO", "Tenés que iniciar sesión.");
                }
            } catch (SlotlyException e) {
                resolver.resolveException(pedido, respuesta, null, e);
                return;
            }
        }
        cadena.doFilter(pedido, respuesta);
    }
}
