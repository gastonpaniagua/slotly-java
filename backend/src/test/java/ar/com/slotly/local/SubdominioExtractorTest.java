package ar.com.slotly.local;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

// Tests unitarios: sin Spring ni base. HU-03 CA3 (normalizar o rechazar hosts raros, nunca error).
class SubdominioExtractorTest {

    private final SubdominioExtractor extractor = new SubdominioExtractor("localhost");

    @ParameterizedTest
    @DisplayName("HU-03 CA3: saca el subdominio de un host válido, en minúsculas y sin puerto")
    @CsvSource({
            "demo.localhost:5173, demo",
            "demo.localhost, demo",
            "DEMO.localhost:5173, demo"
    })
    void devuelveElSubdominio(String host, String esperado) {
        assertThat(extractor.extraer(host)).contains(esperado);
    }

    @ParameterizedTest
    @DisplayName("HU-03 CA3: devuelve vacío si el host no tiene un subdominio nuestro y válido")
    @NullAndEmptySource
    @ValueSource(strings = {
            "localhost:5173",
            "a.b.localhost",
            "demo.otrodominio.com",
            "de mo.localhost",
            "   "
    })
    void devuelveVacio(String host) {
        assertThat(extractor.extraer(host)).isEmpty();
    }
}