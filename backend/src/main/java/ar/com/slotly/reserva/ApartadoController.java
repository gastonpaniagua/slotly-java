package ar.com.slotly.reserva;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.annotation.JsonFormat;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import ar.com.slotly.local.LocalResolver;
import ar.com.slotly.reserva.ApartadoService.Anterior;
import ar.com.slotly.reserva.ApartadoService.Apartado;

// Apartar un horario al pasar al paso 5 de la reserva (HU-11). Público: el cliente no tiene cuenta.
@RestController
@RequestMapping("/api/publico/apartados")
class ApartadoController {

    private final LocalResolver localResolver;
    private final ApartadoService apartados;

    ApartadoController(LocalResolver localResolver, ApartadoService apartados) {
        this.localResolver = localResolver;
        this.apartados = apartados;
    }

    // profesional: el id o "cualquiera". hora: "10:15", en hora del local. anterior: el apartado que se libera.
    record CrearApartadoRequest(
            @NotEmpty(message = "Elegí al menos un servicio.") List<UUID> servicios,
            @NotBlank(message = "Elegí un profesional.") String profesional,
            @NotNull(message = "Elegí un día.") LocalDate fecha,
            @NotNull(message = "Elegí una hora.") @JsonFormat(pattern = "HH:mm") @Schema(type = "string", example = "10:15") LocalTime hora,
            @Valid @Schema(types = {"object", "null"}) AnteriorRequest anterior
    ) {
    }

    record AnteriorRequest(@NotNull UUID id, @NotBlank String token) {
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Aparta un horario por unos minutos mientras el cliente completa sus datos (HU-11)")
    Apartado apartar(@Parameter(hidden = true) @RequestHeader(HttpHeaders.HOST) String host,
            @Valid @RequestBody CrearApartadoRequest pedido) {
        Anterior anterior = pedido.anterior() == null ? null
                : new Anterior(pedido.anterior().id(), pedido.anterior().token());
        return apartados.apartar(localResolver.resolver(host), new ApartadoService.Pedido(
                Set.copyOf(pedido.servicios()), pedido.profesional(), pedido.fecha(), pedido.hora(), anterior));
    }
}
