package ar.com.slotly.publico;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ar.com.slotly.profesional.Horario;

// Tests unitarios: sin Spring ni base. Cómo se juntan las franjas de los profesionales en el horario del local.
class HorarioDeAtencionTest {

    private static final int MARTES = 2;

    @Test
    @DisplayName("HU-02 CA1: devuelve los 7 días y los que no tienen franjas quedan vacíos (cerrado)")
    void devuelveLosSieteDias() {
        List<DiaPublicoResponse> dias = HorarioDeAtencion.calcular(List.of());

        assertThat(dias).extracting(DiaPublicoResponse::diaSemana).containsExactly(0, 1, 2, 3, 4, 5, 6);
        assertThat(dias).allSatisfy(dia -> assertThat(dia.franjas()).isEmpty());
    }

    @Test
    @DisplayName("HU-02 CA1: dos franjas con un hueco en el medio quedan separadas")
    void franjasSeparadasQuedanSeparadas() {
        List<DiaPublicoResponse> dias = HorarioDeAtencion.calcular(List.of(
                franja(MARTES, "10:00", "13:00"),
                franja(MARTES, "16:00", "20:00")));

        assertThat(dias.get(MARTES).franjas()).containsExactly(
                rango("10:00", "13:00"),
                rango("16:00", "20:00"));
    }

    @Test
    @DisplayName("Franjas que se pisan (Ana 10-13, Bruno 12-15) se unen en una sola")
    void franjasQueSePisanSeUnen() {
        List<DiaPublicoResponse> dias = HorarioDeAtencion.calcular(List.of(
                franja(MARTES, "10:00", "13:00"),
                franja(MARTES, "12:00", "15:00")));

        assertThat(dias.get(MARTES).franjas()).containsExactly(rango("10:00", "15:00"));
    }

    @Test
    @DisplayName("Franjas que se tocan (10-13 y 13-16) se unen: el local no cierra a las 13")
    void franjasQueSeTocanSeUnen() {
        List<DiaPublicoResponse> dias = HorarioDeAtencion.calcular(List.of(
                franja(MARTES, "10:00", "13:00"),
                franja(MARTES, "13:00", "16:00")));

        assertThat(dias.get(MARTES).franjas()).containsExactly(rango("10:00", "16:00"));
    }

    @Test
    @DisplayName("Una franja dentro de otra no cambia nada; dos iguales quedan como una")
    void franjasContenidasOIgualesNoSeDuplican() {
        List<DiaPublicoResponse> dias = HorarioDeAtencion.calcular(List.of(
                franja(MARTES, "10:00", "20:00"),
                franja(MARTES, "12:00", "13:00"),
                franja(MARTES, "10:00", "20:00")));

        assertThat(dias.get(MARTES).franjas()).containsExactly(rango("10:00", "20:00"));
    }

    @Test
    @DisplayName("Las franjas salen ordenadas aunque lleguen desordenadas")
    void ordenaLasFranjas() {
        List<DiaPublicoResponse> dias = HorarioDeAtencion.calcular(List.of(
                franja(MARTES, "16:00", "20:00"),
                franja(MARTES, "10:00", "13:00")));

        assertThat(dias.get(MARTES).franjas()).containsExactly(
                rango("10:00", "13:00"),
                rango("16:00", "20:00"));
    }

    // Una franja de un profesional cualquiera: para el cálculo solo importan el día y las horas.
    private static Horario franja(int dia, String desde, String hasta) {
        return new Horario(UUID.randomUUID(), UUID.randomUUID(), dia, LocalTime.parse(desde), LocalTime.parse(hasta));
    }

    private static FranjaPublicaResponse rango(String desde, String hasta) {
        return new FranjaPublicaResponse(LocalTime.parse(desde), LocalTime.parse(hasta));
    }
}
