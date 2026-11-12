package ar.com.slotly.panel;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import ar.com.slotly.local.LocalResolver;
import ar.com.slotly.seguridad.IngresoService;
import ar.com.slotly.usuario.UsuarioSesion;

// Entrar, salir y "¿quién soy?" del panel (HU-16). La sesión vive en Postgres (Spring Session) y el navegador
// solo guarda una cookie httpOnly con su id: JavaScript no la puede leer.
@RestController
class SesionController {

    private final LocalResolver localResolver;
    private final IngresoService ingreso;
    private final SecurityContextRepository contextos;

    SesionController(LocalResolver localResolver, IngresoService ingreso, SecurityContextRepository contextos) {
        this.localResolver = localResolver;
        this.ingreso = ingreso;
        this.contextos = contextos;
    }

    record IngresarRequest(
            @NotBlank(message = "Ingresá tu mail.") @Size(max = 120) String email,
            @NotBlank(message = "Ingresá tu contraseña.") @Size(max = 200) String password) {
    }

    // Lo que el front necesita saber del usuario que entró. profesionalId es null en un admin sin agenda propia.
    record UsuarioActual(String email, String rol, @Schema(types = {"string", "null"}) UUID profesionalId) {

        static UsuarioActual de(UsuarioSesion usuario) {
            return new UsuarioActual(usuario.email(), usuario.rol(), usuario.profesionalId());
        }
    }

    @PostMapping("/api/panel/ingresar")
    @Operation(summary = "Inicia sesión en el panel del local del subdominio (HU-16)")
    UsuarioActual ingresar(@Parameter(hidden = true) @RequestHeader(HttpHeaders.HOST) String host,
            @Valid @RequestBody IngresarRequest pedido, HttpServletRequest request, HttpServletResponse response) {
        UsuarioSesion usuario = ingreso.ingresar(localResolver.resolver(host), pedido.email(), pedido.password());

        // Si ya había una sesión (por ejemplo, la del token CSRF), cambia su id: un id que alguien conocía de antes
        // no sirve para entrar con este usuario (fijación de sesión).
        if (request.getSession(false) != null) {
            request.changeSessionId();
        }
        Authentication autenticacion = new UsernamePasswordAuthenticationToken(usuario, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + usuario.rol().toUpperCase())));
        SecurityContext contexto = SecurityContextHolder.createEmptyContext();
        contexto.setAuthentication(autenticacion);
        SecurityContextHolder.setContext(contexto);
        contextos.saveContext(contexto, request, response);
        return UsuarioActual.de(usuario);
    }

    // HU-16 CA7: la sesión se borra en el servidor; la cookie que quede en otra pestaña ya no sirve.
    // Responde igual aunque no hubiera sesión: salir dos veces no es un error.
    @PostMapping("/api/panel/salir")
    @Operation(summary = "Cierra la sesión del panel (HU-16 CA7)")
    ResponseEntity<Void> salir(HttpServletRequest request, HttpServletResponse response) {
        new SecurityContextLogoutHandler().logout(request, response,
                SecurityContextHolder.getContext().getAuthentication());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/panel/yo")
    @Operation(summary = "El usuario de la sesión actual, o 401 si no hay sesión (HU-16 CA6)")
    UsuarioActual yo(@AuthenticationPrincipal UsuarioSesion usuario) {
        return UsuarioActual.de(usuario);
    }
}
