package ar.com.slotly.disponibilidad;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ar.com.slotly.disponibilidad.MotorDisponibilidad.Agenda;
import ar.com.slotly.disponibilidad.MotorDisponibilidad.Candidato;
import ar.com.slotly.disponibilidad.MotorDisponibilidad.Franja;
import ar.com.slotly.disponibilidad.MotorDisponibilidad.Ocupacion;
import ar.com.slotly.disponibilidad.MotorDisponibilidad.Reglas;
import ar.com.slotly.error.SlotlyException;
import ar.com.slotly.local.Local;
import ar.com.slotly.profesional.Horario;
import ar.com.slotly.profesional.HorarioRepository;
import ar.com.slotly.profesional.Profesional;
import ar.com.slotly.profesional.ProfesionalRepository;
import ar.com.slotly.servicio.Servicio;
import ar.com.slotly.servicio.ServiciosElegidos;
import ar.com.slotly.turno.Turno;
import ar.com.slotly.turno.TurnoRepository;

// Junta de la base lo que necesita el motor (reglas, franjas, ocupaciones) y le pide las horas libres.
// El motor no sabe de la base; este servicio no sabe calcular horas: cada uno hace una cosa.
@Service
@Transactional(readOnly = true)
public class DisponibilidadService {

    // Valor de "profesional" que significa "Cualquiera disponible" (HU-06, HU-07 CA9).
    public static final String CUALQUIERA = "cualquiera";

    private final ServiciosElegidos serviciosElegidos;
    private final ProfesionalRepository profesionalRepository;
    private final HorarioRepository horarioRepository;
    private final TurnoRepository turnoRepository;
    private final Clock reloj;

    DisponibilidadService(ServiciosElegidos serviciosElegidos, ProfesionalRepository profesionalRepository,
            HorarioRepository horarioRepository, TurnoRepository turnoRepository, Clock reloj) {
        this.serviciosElegidos = serviciosElegidos;
        this.profesionalRepository = profesionalRepository;
        this.horarioRepository = horarioRepository;
        this.turnoRepository = turnoRepository;
        this.reloj = reloj;
    }

    // Lo pedido ya validado: con esto se calcula cualquier día.
    // turnos: cuántos turnos (no bloqueos) tiene cada profesional en el rango, para repartir "Cualquiera" (HU-07 CA9).
    record Pedido(Reglas reglas, List<Agenda> agendas, Duration duracion, Duration limpieza, Instant ahora,
            List<Servicio> servicios, List<Profesional> profesionales, Map<UUID, Integer> turnos) {
    }

    // Una hora que se puede reservar: con quién, de cuándo a cuándo (en UTC) y qué servicios.
    public record HorarioReservable(Profesional profesional, Instant inicio, Instant fin, Instant ocupadoHasta,
            List<Servicio> servicios) {
    }

    // ¿Esa hora exacta se puede reservar? Usa la misma regla que decide qué horas se muestran (el motor).
    // Con "cualquiera" elige entre los libres al que tenga menos turnos ese día; si empatan, el primero por orden
    // (HU-07 CA9). Vacío si la hora no existe, no es del intervalo o ya no está libre.
    public Optional<HorarioReservable> reservable(Local local, Set<UUID> servicios, String profesional,
            LocalDate fecha, LocalTime hora) {
        Pedido pedido = armarPedido(local, servicios, profesional, fecha, fecha, null);
        Optional<Instant> inicio = MotorDisponibilidad.instante(pedido.reglas().zona(), fecha, hora);
        if (inicio.isEmpty()) {
            return Optional.empty();
        }
        Map<UUID, Profesional> porId = pedido.profesionales().stream()
                .collect(Collectors.toMap(Profesional::getId, p -> p));
        List<Candidato> libres = pedido.agendas().stream()
                .filter(a -> MotorDisponibilidad.estaLibre(pedido.reglas(), a, fecha, hora, pedido.duracion(),
                        pedido.limpieza(), pedido.ahora()))
                .map(a -> new Candidato(a.profesionalId(), porId.get(a.profesionalId()).getOrden(),
                        pedido.turnos().getOrDefault(a.profesionalId(), 0)))
                .toList();
        Instant fin = inicio.get().plus(pedido.duracion());
        return MotorDisponibilidad.elegirParaCualquiera(libres)
                .map(id -> new HorarioReservable(porId.get(id), inicio.get(), fin, fin.plus(pedido.limpieza()),
                        pedido.servicios()));
    }

    // Las horas libres de un día (HU-07).
    public List<LocalTime> horas(Local local, Set<UUID> servicios, String profesional, LocalDate fecha) {
        return horas(local, servicios, profesional, fecha, null);
    }

    // Igual, pero sin contar el turno ignorar como ocupado: al reprogramar, el horario actual del cliente no le
    // bloquea elegir uno que se pise con él (HU-21 CA1).
    public List<LocalTime> horas(Local local, Set<UUID> servicios, String profesional, LocalDate fecha, UUID ignorar) {
        Pedido pedido = armarPedido(local, servicios, profesional, fecha, fecha, ignorar);
        return MotorDisponibilidad.horasLibres(pedido.reglas(), pedido.agendas(), fecha,
                pedido.duracion(), pedido.limpieza(), pedido.ahora());
    }

    // Qué días entre desde y hasta tienen al menos una hora libre (HU-08), más hoy y el último día permitido.
    // Sin desde, arranca el lunes de esta semana en el local; sin hasta, trae 6 semanas. Así el front nunca
    // necesita saber qué día es hoy (el reloj del celular puede estar en otra zona o mal puesto).
    public DiasConLugar dias(Local local, Set<UUID> servicios, String profesional, LocalDate desde, LocalDate hasta) {
        return dias(local, servicios, profesional, desde, hasta, null);
    }

    // Igual, ignorando ese turno (reprogramar, HU-21 CA1).
    public DiasConLugar dias(Local local, Set<UUID> servicios, String profesional, LocalDate desde, LocalDate hasta,
            UUID ignorar) {
        if (desde == null) {
            LocalDate hoy = MotorDisponibilidad.hoy(reglasDe(local), reloj.instant());
            desde = hoy.minusDays(hoy.getDayOfWeek().getValue() - 1);
        }
        if (hasta == null) {
            hasta = desde.plusDays(41);
        }
        if (hasta.isBefore(desde) || desde.plusDays(41).isBefore(hasta)) {
            throw new SlotlyException(HttpStatus.BAD_REQUEST, "VALIDACION", "El rango de días no es válido (hasta 6 semanas).");
        }
        Pedido pedido = armarPedido(local, servicios, profesional, desde, hasta, ignorar);
        List<DiasConLugar.Dia> dias = desde.datesUntil(hasta.plusDays(1))
                .map(fecha -> new DiasConLugar.Dia(fecha, !MotorDisponibilidad.horasLibres(pedido.reglas(),
                        pedido.agendas(), fecha, pedido.duracion(), pedido.limpieza(), pedido.ahora()).isEmpty()))
                .toList();
        return new DiasConLugar(MotorDisponibilidad.hoy(pedido.reglas(), pedido.ahora()),
                MotorDisponibilidad.ultimoDia(pedido.reglas(), pedido.ahora()), dias);
    }

    public record DiasConLugar(LocalDate hoy, LocalDate ultimoDia, List<Dia> dias) {
        public record Dia(LocalDate fecha, boolean hayLugar) {
        }
    }

    // ignorar: un turno que no cuenta como ocupado (null: ninguno).
    private Pedido armarPedido(Local local, Set<UUID> servicioIds, String profesional, LocalDate desde, LocalDate hasta,
            UUID ignorar) {
        List<Servicio> servicios = serviciosElegidos.validar(local.getId(), servicioIds);
        List<Profesional> profesionales = profesionalesPara(local, servicioIds, profesional);
        Reglas reglas = reglasDe(local);
        ZoneId zona = reglas.zona();
        Instant ahora = reloj.instant();

        Set<UUID> ids = profesionales.stream().map(Profesional::getId).collect(Collectors.toSet());
        Map<UUID, List<Franja>> franjas = horarioRepository.findByLocalIdAndProfesionalIdIn(local.getId(), ids).stream()
                .collect(Collectors.groupingBy(Horario::getProfesionalId,
                        Collectors.mapping(h -> new Franja(h.getDiaSemana(), h.getHoraInicio(), h.getHoraFin()),
                                Collectors.toList())));
        // Las ocupaciones de todo el rango, de 00:00 del primer día a 00:00 del día siguiente al último, en hora del local.
        Instant inicioRango = desde.atStartOfDay(zona).toInstant();
        Instant finRango = hasta.plusDays(1).atStartOfDay(zona).toInstant();
        List<Turno> ocupados = turnoRepository.buscarOcupaciones(local.getId(), ids, inicioRango, finRango, ahora).stream()
                .filter(t -> !t.getId().equals(ignorar))
                .toList();
        Map<UUID, List<Ocupacion>> ocupaciones = ocupados.stream()
                .collect(Collectors.groupingBy(Turno::getProfesionalId,
                        Collectors.mapping(t -> new Ocupacion(t.getInicio(), t.getOcupadoHasta()), Collectors.toList())));
        Map<UUID, Integer> turnos = ocupados.stream()
                .filter(t -> !"bloqueo".equals(t.getTipo()))
                .collect(Collectors.groupingBy(Turno::getProfesionalId, Collectors.summingInt(t -> 1)));

        List<Agenda> agendas = profesionales.stream()
                .map(p -> new Agenda(p.getId(), franjas.getOrDefault(p.getId(), List.of()),
                        ocupaciones.getOrDefault(p.getId(), List.of())))
                .toList();
        return new Pedido(reglas, agendas, duracionTotal(servicios), limpieza(local, servicios), ahora,
                servicios, profesionales, turnos);
    }

    // "cualquiera" → todos los activos que hacen todos los servicios (CA9). Un id → ese, si es activo, del local
    // y hace todos los servicios; si no, 404 con el mismo mensaje para todos los casos (CA11).
    private List<Profesional> profesionalesPara(Local local, Set<UUID> servicios, String profesional) {
        List<Profesional> queHacenTodo = profesionalRepository.buscarActivosQueHacenTodos(local.getId(), servicios, servicios.size());
        if (CUALQUIERA.equals(profesional)) {
            return queHacenTodo;
        }
        UUID id;
        try {
            id = UUID.fromString(profesional);
        } catch (IllegalArgumentException e) {
            throw new SlotlyException(HttpStatus.BAD_REQUEST, "VALIDACION", "El profesional elegido no es válido.");
        }
        return queHacenTodo.stream()
                .filter(p -> p.getId().equals(id))
                .findFirst()
                .map(List::of)
                .orElseThrow(() -> new SlotlyException(HttpStatus.NOT_FOUND, "PROFESIONAL_NO_ENCONTRADO",
                        "Ese profesional no está disponible para los servicios elegidos."));
    }

    private static Reglas reglasDe(Local local) {
        return new Reglas(ZoneId.of(local.getZonaHoraria()), local.getIntervaloMin(), local.getAnticipacionMin(),
                local.getTopeDias());
    }

    // La duración del turno es la suma de los servicios.
    private static Duration duracionTotal(List<Servicio> servicios) {
        return Duration.ofMinutes(servicios.stream().mapToInt(Servicio::getDuracionMin).sum());
    }

    // La limpieza después del turno es la mayor entre la de cada servicio (si tiene) y la del local.
    private static Duration limpieza(Local local, List<Servicio> servicios) {
        int minutos = servicios.stream()
                .mapToInt(s -> s.getLimpiezaMin() != null ? s.getLimpiezaMin() : local.getLimpiezaMin())
                .max()
                .orElse(local.getLimpiezaMin());
        return Duration.ofMinutes(minutos);
    }
}
