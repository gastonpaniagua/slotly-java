package ar.com.slotly.disponibilidad;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ar.com.slotly.disponibilidad.MotorDisponibilidad.Agenda;
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
    record Pedido(Reglas reglas, List<Agenda> agendas, Duration duracion, Duration limpieza, Instant ahora) {
    }

    // Las horas libres de un día (HU-07).
    public List<LocalTime> horas(Local local, Set<UUID> servicios, String profesional, LocalDate fecha) {
        Pedido pedido = armarPedido(local, servicios, profesional, fecha, fecha);
        return MotorDisponibilidad.horasLibres(pedido.reglas(), pedido.agendas(), fecha,
                pedido.duracion(), pedido.limpieza(), pedido.ahora());
    }

    // Qué días entre desde y hasta tienen al menos una hora libre (HU-08), más hoy y el último día permitido.
    public DiasConLugar dias(Local local, Set<UUID> servicios, String profesional, LocalDate desde, LocalDate hasta) {
        if (hasta.isBefore(desde) || desde.plusDays(41).isBefore(hasta)) {
            throw new SlotlyException(HttpStatus.BAD_REQUEST, "VALIDACION", "El rango de días no es válido (hasta 6 semanas).");
        }
        Pedido pedido = armarPedido(local, servicios, profesional, desde, hasta);
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

    private Pedido armarPedido(Local local, Set<UUID> servicioIds, String profesional, LocalDate desde, LocalDate hasta) {
        List<Servicio> servicios = serviciosElegidos.validar(local.getId(), servicioIds);
        List<Profesional> profesionales = profesionalesPara(local, servicioIds, profesional);
        ZoneId zona = ZoneId.of(local.getZonaHoraria());
        Reglas reglas = new Reglas(zona, local.getIntervaloMin(), local.getAnticipacionMin(), local.getTopeDias());
        Instant ahora = reloj.instant();

        Set<UUID> ids = profesionales.stream().map(Profesional::getId).collect(Collectors.toSet());
        Map<UUID, List<Franja>> franjas = horarioRepository.findByLocalIdAndProfesionalIdIn(local.getId(), ids).stream()
                .collect(Collectors.groupingBy(Horario::getProfesionalId,
                        Collectors.mapping(h -> new Franja(h.getDiaSemana(), h.getHoraInicio(), h.getHoraFin()),
                                Collectors.toList())));
        // Las ocupaciones de todo el rango, de 00:00 del primer día a 00:00 del día siguiente al último, en hora del local.
        Instant inicioRango = desde.atStartOfDay(zona).toInstant();
        Instant finRango = hasta.plusDays(1).atStartOfDay(zona).toInstant();
        Map<UUID, List<Ocupacion>> ocupaciones = turnoRepository
                .buscarOcupaciones(local.getId(), ids, inicioRango, finRango, ahora).stream()
                .collect(Collectors.groupingBy(Turno::getProfesionalId,
                        Collectors.mapping(t -> new Ocupacion(t.getInicio(), t.getOcupadoHasta()), Collectors.toList())));

        List<Agenda> agendas = profesionales.stream()
                .map(p -> new Agenda(p.getId(), franjas.getOrDefault(p.getId(), List.of()),
                        ocupaciones.getOrDefault(p.getId(), List.of())))
                .toList();
        return new Pedido(reglas, agendas, duracionTotal(servicios), limpieza(local, servicios), ahora);
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
