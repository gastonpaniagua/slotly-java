package ar.com.slotly.seguridad;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.servlet.HandlerExceptionResolver;

import ar.com.slotly.error.SlotlyException;

// Reglas de acceso: todo cerrado salvo el health y lo público. El login del panel llega en la Etapa 5.
@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain filtros(HttpSecurity http,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) throws Exception {
        http
                .authorizeHttpRequests(reglas -> reglas
                        .requestMatchers("/api/health", "/api/publico/**").permitAll()
                        .anyRequest().authenticated())
                .formLogin(login -> login.disable())
                .httpBasic(basico -> basico.disable())
                // Sin login, el 401 sale por el ApiExceptionHandler: mismo ProblemDetail con "codigo" que el resto.
                .exceptionHandling(errores -> errores
                        .authenticationEntryPoint((pedido, respuesta, excepcion) -> resolver.resolveException(
                                pedido, respuesta, null,
                                new SlotlyException(HttpStatus.UNAUTHORIZED, "NO_AUTENTICADO", "Tenés que iniciar sesión."))));
        return http.build();
    }
}
