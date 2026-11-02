package ar.com.slotly;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.NotBlank;

// Propiedades propias de Slotly (prefijo "slotly" en application.properties). Si falta alguna, la app no arranca.
@ConfigurationProperties(prefix = "slotly")
@Validated
public record SlotlyProperties(
        @NotBlank String dominioBase
) {
}