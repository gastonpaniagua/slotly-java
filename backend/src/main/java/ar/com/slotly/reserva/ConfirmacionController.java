package ar.com.slotly.reserva;

import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import ar.com.slotly.cliente.Telefono;
import ar.com.slotly.local.LocalResolver;
import ar.com.slotly.reserva.ConfirmacionService.DatosDelCliente;
import ar.com.slotly.reserva.ConfirmacionService.TurnoPublico;

// Confirmar la reserva (paso 5 → 6, HU-10). Ver el turno ("Mi turno") está en MiTurnoController.
@RestController
class ConfirmacionController {

    // El token del turno viaja en este header (no en la URL de la API, así no queda en los logs).
    static final String HEADER_TOKEN = "X-Token-Turno";

    private final LocalResolver localResolver;
    private final ConfirmacionService confirmaciones;

    ConfirmacionController(LocalResolver localResolver, ConfirmacionService confirmaciones) {
        this.localResolver = localResolver;
        this.confirmaciones = confirmaciones;
    }

    // HU-10 CA4: cada campo con su mensaje, para mostrarlo junto al campo. El mail es opcional.
    record ConfirmarRequest(
            @NotBlank String token,
            @NotBlank(message = "Ingresá tu nombre.")
            @Size(max = 80, message = "El nombre puede tener hasta 80 caracteres.") String nombre,
            @NotBlank(message = "Ingresá tu celular.") @Telefono String telefono,
            @Schema(types = {"string", "null"})
            @Size(max = 120, message = "El mail es demasiado largo.")
            @Pattern(regexp = "^$|^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$", message = "Revisá el mail.") String email,
            @NotNull(message = "Elegí cómo vas a pagar.")
            @Pattern(regexp = "efectivo|transferencia", message = "Elegí cómo vas a pagar.") String formaPago,
            // HU-10 CA2: sin consentimiento no se reserva.
            @NotNull(message = "Tenés que aceptar la política de privacidad.")
            @AssertTrue(message = "Tenés que aceptar la política de privacidad.") Boolean consentimiento
    ) {
    }

    @PostMapping("/api/publico/apartados/{id}/confirmar")
    @Operation(summary = "Confirma el apartado con los datos del cliente y devuelve el turno (HU-10)")
    TurnoPublico confirmar(@Parameter(hidden = true) @RequestHeader(HttpHeaders.HOST) String host,
            @PathVariable UUID id, @Valid @RequestBody ConfirmarRequest pedido) {
        return confirmaciones.confirmar(localResolver.resolver(host), id, pedido.token(),
                new DatosDelCliente(pedido.nombre(), pedido.telefono(), pedido.email(), pedido.formaPago()));
    }

}
