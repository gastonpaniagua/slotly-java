package ar.com.slotly.reserva;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.annotation.JsonFormat;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import ar.com.slotly.disponibilidad.DisponibilidadService.DiasConLugar;
import ar.com.slotly.disponibilidad.HorasResponse;
import ar.com.slotly.local.LocalResolver;
import ar.com.slotly.reserva.ConfirmacionService.TurnoPublico;
import ar.com.slotly.reserva.MiTurnoService.TurnoReprogramado;

// "Mi turno" (HU-19) y lo que el cliente hace con él: cancelar (HU-20) y reprogramar (HU-21). Se prueba que el turno
// es suyo con el token del link (header X-Token-Turno) o con el acceso de entrar con el teléfono (X-Acceso-Cliente).
@RestController
class MiTurnoController {

    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");
    private static final String TOKEN = ConfirmacionController.HEADER_TOKEN;
    private static final String ACCESO = Credencial.HEADER_ACCESO;

    private final LocalResolver localResolver;
    private final MiTurnoService miTurno;

    MiTurnoController(LocalResolver localResolver, MiTurnoService miTurno) {
        this.localResolver = localResolver;
        this.miTurno = miTurno;
    }

    record ReprogramarRequest(
            @NotNull(message = "Elegí un día.") LocalDate fecha,
            @NotNull(message = "Elegí una hora.") @JsonFormat(pattern = "HH:mm")
            @Schema(type = "string", example = "10:30") LocalTime hora) {
    }

    @GetMapping("/api/publico/turnos/{id}")
    @Operation(summary = "Un turno: la pantalla de confirmación y \"Mi turno\" (HU-10 CA7 y CA9, HU-19)")
    TurnoPublico ver(@Parameter(hidden = true) @RequestHeader(HttpHeaders.HOST) String host, @PathVariable UUID id,
            @RequestHeader(value = TOKEN, required = false) String token,
            @RequestHeader(value = ACCESO, required = false) String acceso) {
        return miTurno.ver(localResolver.resolver(host), id, new Credencial(token, acceso));
    }

    @PostMapping("/api/publico/turnos/{id}/cancelar")
    @Operation(summary = "Cancela el turno, si falta más que el plazo del local (HU-20)")
    TurnoPublico cancelar(@Parameter(hidden = true) @RequestHeader(HttpHeaders.HOST) String host, @PathVariable UUID id,
            @RequestHeader(value = TOKEN, required = false) String token,
            @RequestHeader(value = ACCESO, required = false) String acceso) {
        return miTurno.cancelar(localResolver.resolver(host), id, new Credencial(token, acceso));
    }

    @GetMapping("/api/publico/turnos/{id}/reprogramar/dias")
    @Operation(summary = "Días con lugar para mover el turno: mismos servicios y profesional, sin contar el turno actual (HU-21)")
    DiasConLugar diasParaReprogramar(@Parameter(hidden = true) @RequestHeader(HttpHeaders.HOST) String host,
            @PathVariable UUID id,
            @RequestHeader(value = TOKEN, required = false) String token,
            @RequestHeader(value = ACCESO, required = false) String acceso,
            @RequestParam(required = false) LocalDate desde, @RequestParam(required = false) LocalDate hasta) {
        return miTurno.diasParaReprogramar(localResolver.resolver(host), id, new Credencial(token, acceso), desde, hasta);
    }

    @GetMapping("/api/publico/turnos/{id}/reprogramar/horas")
    @Operation(summary = "Horas libres de un día para mover el turno, sin contar el turno actual (HU-21)")
    HorasResponse horasParaReprogramar(@Parameter(hidden = true) @RequestHeader(HttpHeaders.HOST) String host,
            @PathVariable UUID id,
            @RequestHeader(value = TOKEN, required = false) String token,
            @RequestHeader(value = ACCESO, required = false) String acceso,
            @RequestParam LocalDate fecha) {
        return new HorasResponse(fecha, miTurno
                .horasParaReprogramar(localResolver.resolver(host), id, new Credencial(token, acceso), fecha)
                .stream().map(HORA::format).toList());
    }

    @PostMapping("/api/publico/turnos/{id}/reprogramar")
    @Operation(summary = "Mueve el turno a otro día u hora: el viejo queda cancelado y el nuevo, con su propio link (HU-21)")
    TurnoReprogramado reprogramar(@Parameter(hidden = true) @RequestHeader(HttpHeaders.HOST) String host,
            @PathVariable UUID id,
            @RequestHeader(value = TOKEN, required = false) String token,
            @RequestHeader(value = ACCESO, required = false) String acceso,
            @Valid @RequestBody ReprogramarRequest pedido) {
        return miTurno.reprogramar(localResolver.resolver(host), id, new Credencial(token, acceso), pedido.fecha(),
                pedido.hora());
    }
}
