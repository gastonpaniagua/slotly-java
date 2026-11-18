package ar.com.slotly;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

// Las propiedades de Slotly se validan al arrancar: si una no sirve, la app no arranca (Etapa 9).
class SlotlyPropertiesTest {

    private final Validator validador = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    @DisplayName("Etapa 9: una variable de entorno que falta en producción (queda \"${…}\") no pasa")
    void variableQueFalta() {
        var propiedades = new SlotlyProperties("${SLOTLY_DOMINIO_BASE}", "${SLOTLY_URL_SITIO}", null);

        assertThat(validador.validate(propiedades))
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactlyInAnyOrder("dominioBase", "urlSitio");
    }

    @Test
    @DisplayName("Etapa 9: los valores de desarrollo, de producción y de la demo pasan")
    void valoresValidos() {
        assertThat(validador.validate(new SlotlyProperties("localhost", "http://{subdominio}.localhost:5173", null))).isEmpty();
        assertThat(validador.validate(new SlotlyProperties("slotly.com.ar", "https://{subdominio}.slotly.com.ar", null))).isEmpty();
        assertThat(validador.validate(new SlotlyProperties("onrender.com", "https://slotly-demo.onrender.com", "demo"))).isEmpty();
    }
}
