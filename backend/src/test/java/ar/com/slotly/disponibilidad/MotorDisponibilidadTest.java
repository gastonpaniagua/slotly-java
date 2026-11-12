package ar.com.slotly.disponibilidad;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import ar.com.slotly.disponibilidad.MotorDisponibilidad.Agenda;
import ar.com.slotly.disponibilidad.MotorDisponibilidad.Candidato;
import ar.com.slotly.disponibilidad.MotorDisponibilidad.Franja;
import ar.com.slotly.disponibilidad.MotorDisponibilidad.Ocupacion;
import ar.com.slotly.disponibilidad.MotorDisponibilidad.Reglas;

// Tests unitarios del motor: sin Spring ni base, con fecha y hora fijas (doc 06: nunca la hora real).
class MotorDisponibilidadTest {

    private static final ZoneId BUENOS_AIRES = ZoneId.of("America/Argentina/Buenos_Aires");
    // Intervalo 15 min, anticipación 1 h, tope 30 días: los valores del local demo.
    private static final Reglas REGLAS = new Reglas(BUENOS_AIRES, 15, 60, 30);

    // Martes 17/11/2026 (diaSemana 2). "Ahora" es el lunes anterior, así la anticipación no molesta.
    private static final LocalDate MARTES = LocalDate.of(2026, 11, 17);
    private static final int DIA_MARTES = 2;
    private static final Instant LUNES_9 = enBuenosAires(LocalDate.of(2026, 11, 16), "09:00");

    @Test
    @DisplayName("HU-07 CA1: con un servicio de 1 h y cierre a las 20:00, la última opción es 19:00")
    void ultimaOpcionAntesDelCierre() {
        List<LocalTime> horas = horas(agenda(franja("16:00", "20:00")), Duration.ofMinutes(60));

        assertThat(horas).last().isEqualTo(LocalTime.of(19, 0));
        assertThat(horas).doesNotContain(LocalTime.of(19, 15), LocalTime.of(19, 30), LocalTime.of(19, 45));
    }

    @Test
    @DisplayName("HU-07 CA2: con 45 min y pausa de 13:00 a 16:00, aparece 12:15 y no 12:30 (nada cruza la pausa)")
    void nadaCruzaLaPausa() {
        List<LocalTime> horas = horas(agenda(franja("10:00", "13:00"), franja("16:00", "20:00")), Duration.ofMinutes(45));

        assertThat(horas).contains(LocalTime.of(12, 15), LocalTime.of(16, 0));
        assertThat(horas).doesNotContain(LocalTime.of(12, 30), LocalTime.of(12, 45), LocalTime.of(15, 45));
        // Después de 12:15 la siguiente es 16:00.
        assertThat(horas.get(horas.indexOf(LocalTime.of(12, 15)) + 1)).isEqualTo(LocalTime.of(16, 0));
    }

    @Test
    @DisplayName("HU-07 CA3: con un turno de 10:00 a 10:45 y un servicio de 30 min, no aparecen 9:45 a 10:30 y sí 10:45")
    void noSeCruzaConUnTurno() {
        Agenda agenda = agenda(List.of(franja("09:00", "13:00")), List.of(ocupacion("10:00", "10:45")));

        List<LocalTime> horas = horas(agenda, Duration.ofMinutes(30));

        assertThat(horas).doesNotContain(LocalTime.of(9, 45), LocalTime.of(10, 0), LocalTime.of(10, 15), LocalTime.of(10, 30));
        assertThat(horas).contains(LocalTime.of(9, 30), LocalTime.of(10, 45));
    }

    @Test
    @DisplayName("HU-07 CA4: con un bloqueo de 11:00 a 12:00, ninguna opción se cruza con el bloqueo")
    void noSeCruzaConUnBloqueo() {
        Agenda agenda = agenda(List.of(franja("10:00", "13:00")), List.of(ocupacion("11:00", "12:00")));

        List<LocalTime> horas = horas(agenda, Duration.ofMinutes(30));

        assertThat(horas).doesNotContain(LocalTime.of(10, 45), LocalTime.of(11, 0), LocalTime.of(11, 30), LocalTime.of(11, 45));
        assertThat(horas).contains(LocalTime.of(10, 30), LocalTime.of(12, 0));
    }

    @Test
    @DisplayName("HU-07 CA6: con anticipación de 1 h y son las 10:20, la primera opción de hoy es 11:30")
    void respetaLaAnticipacion() {
        Instant martes1020 = enBuenosAires(MARTES, "10:20");

        List<LocalTime> horas = MotorDisponibilidad.horasLibres(REGLAS, List.of(agenda(franja("10:00", "13:00"))),
                MARTES, Duration.ofMinutes(30), Duration.ZERO, martes1020);

        assertThat(horas).first().isEqualTo(LocalTime.of(11, 30));
    }

    @Test
    @DisplayName("HU-07 CA7: con tope de 30 días, el día 30 tiene horas y el 31 no; un día pasado tampoco")
    void respetaElTope() {
        Agenda todosLosDias = agenda(franjasTodaLaSemana("10:00", "13:00"));
        LocalDate hoy = LocalDate.of(2026, 11, 16);

        assertThat(MotorDisponibilidad.horasLibres(REGLAS, List.of(todosLosDias), hoy.plusDays(30),
                Duration.ofMinutes(30), Duration.ZERO, LUNES_9)).isNotEmpty();
        assertThat(MotorDisponibilidad.horasLibres(REGLAS, List.of(todosLosDias), hoy.plusDays(31),
                Duration.ofMinutes(30), Duration.ZERO, LUNES_9)).isEmpty();
        assertThat(MotorDisponibilidad.horasLibres(REGLAS, List.of(todosLosDias), hoy.minusDays(1),
                Duration.ofMinutes(30), Duration.ZERO, LUNES_9)).isEmpty();
        assertThat(MotorDisponibilidad.ultimoDia(REGLAS, LUNES_9)).isEqualTo(hoy.plusDays(30));
    }

    @Test
    @DisplayName("HU-07 CA8: con limpieza de 10 min y un turno que termina 10:45, la siguiente opción es 11:00")
    void respetaLaLimpiezaDelTurnoAnterior() {
        // El turno termina 10:45 y con la limpieza ocupa hasta 10:55.
        Agenda agenda = agenda(List.of(franja("10:00", "13:00")), List.of(ocupacion("10:00", "10:55")));

        List<LocalTime> horas = horas(agenda, Duration.ofMinutes(30));

        assertThat(horas).doesNotContain(LocalTime.of(10, 45));
        assertThat(horas).first().isEqualTo(LocalTime.of(11, 0));
    }

    @Test
    @DisplayName("La limpieza del turno nuevo no puede pisar el turno siguiente, pero sí puede pasar del cierre")
    void laLimpiezaDelTurnoNuevo() {
        Agenda agenda = agenda(List.of(franja("10:00", "20:00")), List.of(ocupacion("11:00", "11:30")));
        Duration limpieza = Duration.ofMinutes(10);

        List<LocalTime> horas = MotorDisponibilidad.horasLibres(REGLAS, List.of(agenda), MARTES,
                Duration.ofMinutes(30), limpieza, LUNES_9);

        // 10:30 terminaría 11:00 y con limpieza ocuparía hasta 11:10: pisa el turno de las 11:00.
        assertThat(horas).contains(LocalTime.of(10, 15)).doesNotContain(LocalTime.of(10, 30));
        // 19:30 termina 20:00 justo al cierre; la limpieza hasta 20:10 no importa.
        assertThat(horas).last().isEqualTo(LocalTime.of(19, 30));
    }

    @Test
    @DisplayName("Una franja que no empieza en punto ofrece horas en punto del intervalo (10:10 → 10:15)")
    void horasEnPuntoDelIntervalo() {
        List<LocalTime> horas = horas(agenda(franja("10:10", "11:00")), Duration.ofMinutes(15));

        assertThat(horas).containsExactly(LocalTime.of(10, 15), LocalTime.of(10, 30), LocalTime.of(10, 45));
    }

    @Test
    @DisplayName("Un día sin franjas no tiene horas")
    void diaSinFranjas() {
        Agenda soloLunes = agenda(new Franja(1, LocalTime.of(10, 0), LocalTime.of(13, 0)));

        assertThat(horas(soloLunes, Duration.ofMinutes(30))).isEmpty();
    }

    @Nested
    @DisplayName("HU-07 CA9: Cualquiera disponible")
    class Cualquiera {

        @Test
        @DisplayName("aparece cada hora en la que al menos un profesional está libre")
        void unionDeLosProfesionales() {
            Agenda ana = agenda(List.of(franja("10:00", "13:00")), List.of(ocupacion("10:00", "12:00")));
            Agenda bruno = agenda(List.of(franja("10:00", "13:00")), List.of(ocupacion("10:00", "11:00")));

            List<LocalTime> horas = MotorDisponibilidad.horasLibres(REGLAS, List.of(ana, bruno), MARTES,
                    Duration.ofMinutes(60), Duration.ZERO, LUNES_9);

            // 10:00 los dos están ocupados; 11:00 solo Bruno está libre; 12:00 los dos.
            assertThat(horas).containsExactly(LocalTime.of(11, 0), LocalTime.of(11, 15), LocalTime.of(11, 30),
                    LocalTime.of(11, 45), LocalTime.of(12, 0));
        }

        @Test
        @DisplayName("se asigna al que tenga menos turnos ese día")
        void asignaAlQueTieneMenosTurnos() {
            UUID ana = UUID.randomUUID();
            UUID bruno = UUID.randomUUID();

            assertThat(MotorDisponibilidad.elegirParaCualquiera(List.of(
                    new Candidato(ana, 1, 3), new Candidato(bruno, 2, 1)))).contains(bruno);
        }

        @Test
        @DisplayName("si empatan, se asigna al primero por orden")
        void empateAlPrimeroPorOrden() {
            UUID ana = UUID.randomUUID();
            UUID bruno = UUID.randomUUID();

            assertThat(MotorDisponibilidad.elegirParaCualquiera(List.of(
                    new Candidato(bruno, 2, 1), new Candidato(ana, 1, 1)))).contains(ana);
            assertThat(MotorDisponibilidad.elegirParaCualquiera(List.of())).isEmpty();
        }
    }

    @Nested
    @DisplayName("HU-07 CA10: cambio de horario de verano (zona con horario de verano: Nueva York)")
    class HorarioDeVerano {

        private static final ZoneId NUEVA_YORK = ZoneId.of("America/New_York");
        private static final Reglas REGLAS_NY = new Reglas(NUEVA_YORK, 15, 0, 30);

        @Test
        @DisplayName("el día que el reloj salta de 2:00 a 3:00 no se ofrecen horas que no existen y no se pierde ninguna")
        void saltoHaciaAdelante() {
            LocalDate domingo = LocalDate.of(2026, 3, 8);
            Instant antes = domingo.minusDays(1).atStartOfDay(NUEVA_YORK).toInstant();

            List<LocalTime> horas = MotorDisponibilidad.horasLibres(REGLAS_NY,
                    List.of(agenda(new Franja(0, LocalTime.of(1, 0), LocalTime.of(4, 0)))),
                    domingo, Duration.ofMinutes(60), Duration.ZERO, antes);

            // De 2:00 a 2:59 no existen ese día.
            assertThat(horas).containsExactly(LocalTime.of(1, 0), LocalTime.of(1, 15), LocalTime.of(1, 30),
                    LocalTime.of(1, 45), LocalTime.of(3, 0));
        }

        @Test
        @DisplayName("el día que el reloj vuelve de 2:00 a 1:00 cada hora aparece una sola vez")
        void saltoHaciaAtras() {
            LocalDate domingo = LocalDate.of(2026, 11, 1);
            Instant antes = domingo.minusDays(1).atStartOfDay(NUEVA_YORK).toInstant();

            List<LocalTime> horas = MotorDisponibilidad.horasLibres(REGLAS_NY,
                    List.of(agenda(new Franja(0, LocalTime.of(0, 0), LocalTime.of(3, 0)))),
                    domingo, Duration.ofMinutes(60), Duration.ZERO, antes);

            // De 0:00 a 2:00 cada 15 min: 9 horas, sin repetir 1:00 a 1:45 aunque ese día existan dos veces.
            assertThat(horas).hasSize(9).doesNotHaveDuplicates();
            assertThat(horas).first().isEqualTo(LocalTime.of(0, 0));
            assertThat(horas).last().isEqualTo(LocalTime.of(2, 0));
        }
    }

    // ---- Ayudas ----

    private static List<LocalTime> horas(Agenda agenda, Duration duracion) {
        return MotorDisponibilidad.horasLibres(REGLAS, List.of(agenda), MARTES, duracion, Duration.ZERO, LUNES_9);
    }

    private static Franja franja(String desde, String hasta) {
        return new Franja(DIA_MARTES, LocalTime.parse(desde), LocalTime.parse(hasta));
    }

    private static List<Franja> franjasTodaLaSemana(String desde, String hasta) {
        List<Franja> franjas = new ArrayList<>();
        for (int dia = 0; dia <= 6; dia++) {
            franjas.add(new Franja(dia, LocalTime.parse(desde), LocalTime.parse(hasta)));
        }
        return franjas;
    }

    private static Agenda agenda(Franja... franjas) {
        return agenda(List.of(franjas), List.of());
    }

    private static Agenda agenda(List<Franja> franjas) {
        return agenda(franjas, List.of());
    }

    private static Agenda agenda(List<Franja> franjas, List<Ocupacion> ocupaciones) {
        return new Agenda(UUID.randomUUID(), franjas, ocupaciones);
    }

    // Algo que ocupa el martes de desde a hasta, en hora de Buenos Aires.
    private static Ocupacion ocupacion(String desde, String hasta) {
        return new Ocupacion(enBuenosAires(MARTES, desde), enBuenosAires(MARTES, hasta));
    }

    private static Instant enBuenosAires(LocalDate fecha, String hora) {
        return LocalDateTime.of(fecha, LocalTime.parse(hora)).atZone(BUENOS_AIRES).toInstant();
    }
}
