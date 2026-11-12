package ar.com.slotly.reserva;

import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;

import ar.com.slotly.local.LocalResolver;
import ar.com.slotly.reserva.ConfirmacionService.TurnoPublico;

// Acciones del cliente sobre su turno, con el token del link en el header (como "ver", en ConfirmacionController).
@RestController
class MiTurnoController {

    private final LocalResolver localResolver;
    private final MiTurnoService miTurno;

    MiTurnoController(LocalResolver localResolver, MiTurnoService miTurno) {
        this.localResolver = localResolver;
        this.miTurno = miTurno;
    }

    @PostMapping("/api/publico/turnos/{id}/cancelar")
    @Operation(summary = "Cancela el turno desde su link, si falta más que el plazo del local (HU-20)")
    TurnoPublico cancelar(@Parameter(hidden = true) @RequestHeader(HttpHeaders.HOST) String host,
            @PathVariable UUID id, @RequestHeader(ConfirmacionController.HEADER_TOKEN) String token) {
        return miTurno.cancelar(localResolver.resolver(host), id, token);
    }
}
