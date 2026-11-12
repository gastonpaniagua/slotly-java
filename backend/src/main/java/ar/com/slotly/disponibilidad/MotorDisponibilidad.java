package ar.com.slotly.disponibilidad;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.IntStream;

// Calcula las horas de inicio libres (HU-07). Java puro: no sabe de Spring ni de la base, todo le llega como datos.
// Las franjas se piensan en hora del local y los turnos en UTC; la conversión se hace con la zona del local,
// así un día con cambio de horario de verano no duplica ni pierde horas (CA10).
public final class MotorDisponibilidad {

    // Reglas del local que importan para el cálculo.
    public record Reglas(ZoneId zona, int intervaloMin, int anticipacionMin, int topeDias) {
    }

    // Una franja del horario semanal de un profesional, en hora del local. diaSemana: 0 domingo … 6 sábado.
    public record Franja(int diaSemana, LocalTime desde, LocalTime hasta) {
    }

    // Algo que ocupa al profesional (turno, bloqueo o apartado vigente), en UTC, de desde a hasta (fin + limpieza).
    public record Ocupacion(Instant desde, Instant hasta) {
    }

    // Lo que hace falta saber de un profesional: cuándo trabaja y qué tiene ocupado.
    public record Agenda(UUID profesionalId, List<Franja> franjas, List<Ocupacion> ocupaciones) {
    }

    // Un profesional libre en el horario pedido, para repartir los turnos de "Cualquiera disponible" (CA9).
    public record Candidato(UUID profesionalId, int orden, int turnosDelDia) {
    }

    private MotorDisponibilidad() {
    }

    // Horas de inicio libres de ese día, en hora del local y ordenadas.
    // Con una agenda es un profesional; con varias es "Cualquiera disponible": alcanza con que uno esté libre (CA9).
    public static List<LocalTime> horasLibres(Reglas reglas, List<Agenda> agendas, LocalDate fecha,
            Duration duracion, Duration limpieza, Instant ahora) {
        TreeSet<LocalTime> horas = new TreeSet<>();
        if (!dentroDelTope(reglas, fecha, ahora)) {
            return List.of();
        }
        for (Agenda agenda : agendas) {
            for (Franja franja : franjasDelDia(agenda, fecha)) {
                for (LocalTime hora : iniciosPosibles(franja, reglas.intervaloMin(), duracion)) {
                    if (estaLibre(reglas, agenda, fecha, hora, duracion, limpieza, ahora)) {
                        horas.add(hora);
                    }
                }
            }
        }
        return List.copyOf(horas);
    }

    // true si el profesional puede empezar a esa hora: trabaja, entra completo, respeta anticipación y tope,
    // y no se cruza con nada (contando la limpieza del turno nuevo, que no puede pisar el siguiente).
    public static boolean estaLibre(Reglas reglas, Agenda agenda, LocalDate fecha, LocalTime hora,
            Duration duracion, Duration limpieza, Instant ahora) {
        if (minutosDelDia(hora) % reglas.intervaloMin() != 0) {
            return false; // solo horas en punto del intervalo (:00, :15…), como las que se ofrecen
        }
        if (!dentroDelTope(reglas, fecha, ahora) || !entraEnUnaFranja(agenda, fecha, hora, duracion)) {
            return false;
        }
        Optional<Instant> inicio = instante(reglas.zona(), fecha, hora);
        if (inicio.isEmpty()) {
            return false; // esa hora no existe ese día (salto del horario de verano)
        }
        if (inicio.get().isBefore(ahora.plus(Duration.ofMinutes(reglas.anticipacionMin())))) {
            return false; // CA6: anticipación mínima
        }
        Instant ocupadoHasta = inicio.get().plus(duracion).plus(limpieza);
        return agenda.ocupaciones().stream()
                .noneMatch(o -> inicio.get().isBefore(o.hasta()) && ocupadoHasta.isAfter(o.desde()));
    }

    // A quién asignar un turno de "Cualquiera disponible": al que tenga menos turnos ese día; si empatan,
    // al primero por orden (CA9). Recibe solo los que están libres en ese horario.
    public static Optional<UUID> elegirParaCualquiera(List<Candidato> libres) {
        return libres.stream()
                .min(Comparator.comparingInt(Candidato::turnosDelDia).thenComparingInt(Candidato::orden))
                .map(Candidato::profesionalId);
    }

    // El día de hoy en el local (no en UTC: a las 22:00 en Argentina ya es mañana en UTC).
    public static LocalDate hoy(Reglas reglas, Instant ahora) {
        return LocalDate.ofInstant(ahora, reglas.zona());
    }

    // Último día que se puede reservar (CA7: con tope de 30 días, hoy + 30 sí; hoy + 31 no).
    public static LocalDate ultimoDia(Reglas reglas, Instant ahora) {
        return hoy(reglas, ahora).plusDays(reglas.topeDias());
    }

    private static boolean dentroDelTope(Reglas reglas, LocalDate fecha, Instant ahora) {
        return !fecha.isBefore(hoy(reglas, ahora)) && !fecha.isAfter(ultimoDia(reglas, ahora));
    }

    private static List<Franja> franjasDelDia(Agenda agenda, LocalDate fecha) {
        int dia = diaSemana(fecha.getDayOfWeek());
        return agenda.franjas().stream().filter(f -> f.diaSemana() == dia).toList();
    }

    // Las horas "en punto" del intervalo (:00, :15, :30, :45) en las que lo pedido entra completo en la franja.
    private static List<LocalTime> iniciosPosibles(Franja franja, int intervaloMin, Duration duracion) {
        int desde = minutosDelDia(franja.desde());
        int hasta = minutosDelDia(franja.hasta());
        int primero = (desde + intervaloMin - 1) / intervaloMin * intervaloMin; // redondea para arriba
        int largo = (int) duracion.toMinutes();
        return IntStream.iterate(primero, m -> m + largo <= hasta, m -> m + intervaloMin)
                .mapToObj(m -> LocalTime.of(m / 60, m % 60))
                .toList();
    }

    // CA1 y CA2: el servicio tiene que terminar dentro de una franja (la limpieza sí puede pasar del cierre).
    private static boolean entraEnUnaFranja(Agenda agenda, LocalDate fecha, LocalTime hora, Duration duracion) {
        int inicio = minutosDelDia(hora);
        int fin = inicio + (int) duracion.toMinutes();
        return franjasDelDia(agenda, fecha).stream()
                .anyMatch(f -> inicio >= minutosDelDia(f.desde()) && fin <= minutosDelDia(f.hasta()));
    }

    // La hora local como instante. Vacío si no existe ese día (cuando el reloj salta por el horario de verano).
    // Si existe dos veces (cuando el reloj vuelve atrás), Java toma la primera: la hora se ofrece una sola vez.
    public static Optional<Instant> instante(ZoneId zona, LocalDate fecha, LocalTime hora) {
        LocalDateTime local = LocalDateTime.of(fecha, hora);
        ZonedDateTime conZona = local.atZone(zona);
        if (!conZona.toLocalDateTime().equals(local)) {
            return Optional.empty();
        }
        return Optional.of(conZona.toInstant());
    }

    // Java numera de lunes (1) a domingo (7); la base, de domingo (0) a sábado (6).
    private static int diaSemana(DayOfWeek dia) {
        return dia.getValue() % 7;
    }

    private static int minutosDelDia(LocalTime hora) {
        return hora.getHour() * 60 + hora.getMinute();
    }
}
