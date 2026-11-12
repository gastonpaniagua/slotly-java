package ar.com.slotly.panel;

import java.time.LocalDate;

import org.springframework.http.HttpHeaders;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;

import ar.com.slotly.local.LocalResolver;
import ar.com.slotly.panel.AgendaService.Agenda;
import ar.com.slotly.usuario.UsuarioSesion;

// La agenda del panel. Necesita sesión (SecurityConfig) y que sea del local del subdominio (SesionDelLocalFilter).
@RestController
class AgendaController {

    private final LocalResolver localResolver;
    private final AgendaService agendas;

    AgendaController(LocalResolver localResolver, AgendaService agendas) {
        this.localResolver = localResolver;
        this.agendas = agendas;
    }

    @GetMapping("/api/panel/agenda")
    @Operation(summary = "Turnos, bloqueos y horario de trabajo de 1 o 7 días (HU-17 y HU-18)")
    Agenda ver(@Parameter(hidden = true) @RequestHeader(HttpHeaders.HOST) String host,
            @AuthenticationPrincipal UsuarioSesion usuario,
            @Parameter(description = "Primer día, en hora del local. Si falta, hoy") @RequestParam(required = false) LocalDate desde,
            @Parameter(description = "1 (día) o 7 (semana)") @RequestParam int dias,
            @Parameter(description = "Id del profesional o \"todos\" (solo el admin). Si falta: un profesional ve la suya y el admin, la de todos")
            @RequestParam(required = false) String profesional) {
        return agendas.ver(localResolver.resolver(host), usuario, desde, dias, profesional);
    }
}
