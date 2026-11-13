package ar.com.slotly.admin;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// D10: el tipo de una imagen sale de sus primeros bytes, no del nombre ni de lo que dice el navegador.
class TipoDeImagenTest {

    @Test
    @DisplayName("reconoce PNG, JPG y WebP por su firma")
    void formatosAceptados() {
        assertThat(ImagenesController.tipoDe(bytes(0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0))).hasValue("image/png");
        assertThat(ImagenesController.tipoDe(bytes(0xFF, 0xD8, 0xFF, 0xE0))).hasValue("image/jpeg");
        assertThat(ImagenesController.tipoDe(bytes('R', 'I', 'F', 'F', 1, 2, 3, 4, 'W', 'E', 'B', 'P'))).hasValue("image/webp");
    }

    @Test
    @DisplayName("rechaza otros formatos (GIF, SVG, texto) y archivos cortados")
    void formatosRechazados() {
        assertThat(ImagenesController.tipoDe(bytes('G', 'I', 'F', '8', '9', 'a'))).isEmpty();
        assertThat(ImagenesController.tipoDe("<svg xmlns=\"http://www.w3.org/2000/svg\"/>".getBytes())).isEmpty();
        assertThat(ImagenesController.tipoDe(bytes('R', 'I', 'F', 'F', 1, 2, 3, 4, 'W', 'A', 'V', 'E'))).isEmpty();
        assertThat(ImagenesController.tipoDe(bytes(0x89, 'P'))).isEmpty();
        assertThat(ImagenesController.tipoDe(new byte[0])).isEmpty();
    }

    private static byte[] bytes(int... valores) {
        byte[] datos = new byte[valores.length];
        for (int i = 0; i < valores.length; i++) {
            datos[i] = (byte) valores[i];
        }
        return datos;
    }
}
