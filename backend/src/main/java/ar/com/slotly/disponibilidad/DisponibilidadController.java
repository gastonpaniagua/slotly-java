package ar.com.slotly.disponibilidad;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;

import ar.com.slotly.disponibilidad.DisponibilidadService.DiasConLugar;
import ar.com.slotly.local.Local;
import ar.com.slotly.local.LocalResolver;

// Horas y días libres para los pasos 3 y 4 de la reserva. Público: cuelga de /api/publico.
// "profesional" es el id de un profesional o "cualquiera".
@RestController
@RequestMapping("/api/publico/disponibilidad")
class DisponibilidadController {

    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    private final LocalResolver localResolver;
    private final DisponibilidadService disponibilidad;

    DisponibilidadController(LocalResolver localResolver, DisponibilidadService disponibilidad) {
        this.localResolver = localResolver;
        this.disponibilidad = disponibilidad;
    }

    @GetMapping("/horas")
    @Operation(summary = "Horas de inicio libres de un día para los servicios y el profesional elegidos (HU-07)")
    HorasResponse horas(@Parameter(hidden = true) @RequestHeader(HttpHeaders.HOST) String host,
            @RequestParam Set<UUID> servicios, @RequestParam String profesional, @RequestParam LocalDate fecha) {
        Local local = localResolver.resolver(host);
        List<String> horas = disponibilidad.horas(local, servicios, profesional, fecha).stream()
                .map(HORA::format)
                .toList();
        return new HorasResponse(fecha, horas);
    }

    @GetMapping("/dias")
    @Operation(summary = "Qué días de un rango (hasta 6 semanas) tienen al menos una hora libre (HU-08). "
            + "Sin desde: el lunes de esta semana en el local; sin hasta: 6 semanas")
    DiasConLugar dias(@Parameter(hidden = true) @RequestHeader(HttpHeaders.HOST) String host,
            @RequestParam Set<UUID> servicios, @RequestParam String profesional,
            @RequestParam(required = false) LocalDate desde, @RequestParam(required = false) LocalDate hasta) {
        Local local = localResolver.resolver(host);
        return disponibilidad.dias(local, servicios, profesional, desde, hasta);
    }
}
