package ar.com.slotly.cliente;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

// Tests unitarios: cómo se normalizan los teléfonos (HU-10 CA3 y CA4).
class TelefonosTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "11 2345-6789          | +5491123456789",
            "+54 9 11 2345 6789    | +5491123456789",
            "011 15 2345-6789      | +5491123456789",
            "(11) 15-2345-6789     | +5491123456789",
            "1123456789            | +5491123456789",
            "+5491123456789        | +5491123456789",
            "353 400-0000          | +5493534000000",
            "0353 15 400-0000      | +5493534000000",
            "+54 9 353 409-9337    | +5493534099337",
    })
    @DisplayName("HU-10 CA3: un celular argentino escrito de distintas formas queda igual (+549…)")
    void normalizaCelularesArgentinos(String escrito, String esperado) {
        assertThat(Telefonos.normalizar(escrito)).contains(esperado);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "123", "no es un teléfono", "11 2345", "+54 9 11 2345 6789 0000"})
    @DisplayName("HU-10 CA4: un teléfono inválido no se normaliza")
    void rechazaInvalidos(String escrito) {
        assertThat(Telefonos.normalizar(escrito)).isEmpty();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "+598 94 123 456     | +59894123456",
            "+1 (415) 555-2671   | +14155552671",
    })
    @DisplayName("Un celular de otro país se acepta en formato internacional")
    void aceptaOtrosPaises(String escrito, String esperado) {
        assertThat(Telefonos.normalizar(escrito)).contains(esperado);
    }
}
