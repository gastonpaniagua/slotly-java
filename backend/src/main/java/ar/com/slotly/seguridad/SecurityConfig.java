package ar.com.slotly.seguridad;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;
import org.springframework.web.servlet.HandlerExceptionResolver;

import ar.com.slotly.error.SlotlyException;

// Reglas de acceso: lo público y el login abiertos; el panel, con sesión; lo de admin, solo para el admin.
@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain filtros(HttpSecurity http, SecurityContextRepository contextos,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) throws Exception {
        http
                .authorizeHttpRequests(reglas -> reglas
                        .requestMatchers("/api/health", "/api/publico/**").permitAll()
                        .requestMatchers("/api/panel/ingresar", "/api/panel/salir").permitAll()
                        // Documentación de la API (springdoc): solo describe endpoints, no expone datos.
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        // HU-18 CA3: un profesional que usa un endpoint de admin recibe 403.
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                // La sesión (con el usuario adentro) se guarda y se lee de la HttpSession, que Spring Session
                // guarda en Postgres. El login la guarda a mano con este mismo repositorio.
                .securityContext(contexto -> contexto.securityContextRepository(contextos))
                // CSRF protege a quien tiene una sesión iniciada: el panel. Modo SPA: el token viaja en la cookie
                // XSRF-TOKEN (legible por el front) y el front lo devuelve en el header X-XSRF-TOKEN.
                // El flujo público no usa sesión (la reserva se identifica con un token dentro del pedido): ahí no aplica.
                .csrf(csrf -> csrf.spa().ignoringRequestMatchers("/api/publico/**"))
                // Spring Security guarda el pedido rechazado en una sesión para "volver después del login". Eso lo
                // hace el front (HU-16 CA6); acá crearía una sesión vacía en la base por cada visitante sin login.
                .requestCache(cache -> cache.disable())
                .formLogin(login -> login.disable())
                .httpBasic(basico -> basico.disable())
                // Los errores de seguridad salen por el ApiExceptionHandler: mismo ProblemDetail con "codigo".
                .exceptionHandling(errores -> errores
                        .authenticationEntryPoint((pedido, respuesta, excepcion) -> resolver.resolveException(
                                pedido, respuesta, null,
                                new SlotlyException(HttpStatus.UNAUTHORIZED, "NO_AUTENTICADO", "Tenés que iniciar sesión.")))
                        // Con sesión pero sin permiso (o sin el token CSRF): 403.
                        .accessDeniedHandler((pedido, respuesta, excepcion) -> resolver.resolveException(
                                pedido, respuesta, null,
                                new SlotlyException(HttpStatus.FORBIDDEN, "SIN_PERMISO", "No tenés permiso para hacer esto."))));
        return http.build();
    }

    // La cookie de la sesión del panel (D1): JavaScript no la puede leer (HttpOnly) y otro sitio no la puede usar en
    // un pedido que no sea navegar (SameSite=Lax). Secure lo pone sola cuando el pedido llega por https (producción).
    // Sin dominio: queda atada al subdominio del local, así el navegador no la manda a otro local (HU-16 CA5).
    @Bean
    CookieSerializer cookieDeSesion() {
        DefaultCookieSerializer cookie = new DefaultCookieSerializer();
        cookie.setCookieName("SLOTLY_SESION");
        cookie.setUseHttpOnlyCookie(true);
        cookie.setSameSite("Lax");
        return cookie;
    }

    @Bean
    SecurityContextRepository contextos() {
        return new HttpSessionSecurityContextRepository();
    }

    // argon2id con los parámetros que recomienda Spring Security (D1, HU-16 CA8).
    @Bean
    PasswordEncoder passwordEncoder() {
        return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    }
}
