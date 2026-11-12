package ar.com.slotly.panel;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.annotation.JsonFormat;

import io.swagger.v3.oas.annotations.media.Schema;

import ar.com.slotly.error.SlotlyException;
import ar.com.slotly.local.Local;
import ar.com.slotly.profesional.Horario;
import ar.com.slotly.profesional.HorarioRepository;
import ar.com.slotly.profesional.Profesional;
import ar.com.slotly.profesional.ProfesionalRepository;
import ar.com.slotly.usuario.UsuarioSesion;

// La agenda del panel (HU-17 y HU-18): turnos, bloqueos y horario de trabajo de uno o varios profesionales,
// del día o de la semana. Todo en hora del local: el front no convierte zonas.
@Service
class AgendaService {

    static final String TODOS = "todos";

    private final JdbcTemplate jdbc;
    private final ProfesionalRepository profesionales;
    private final HorarioRepository horarios;

    AgendaService(JdbcTemplate jdbc, ProfesionalRepository profesionales, HorarioRepository horarios) {
        this.jdbc = jdbc;
        this.profesionales = profesionales;
        this.horarios = horarios;
    }

    record ProfesionalDeAgenda(UUID id, String nombre) {
    }

    // Una franja del horario de trabajo ese día. Entre dos franjas hay una pausa (HU-17 CA5).
    record FranjaDeAgenda(UUID profesionalId, LocalDate fecha,
            @JsonFormat(pattern = "HH:mm") @Schema(type = "string", example = "10:00") LocalTime desde,
            @JsonFormat(pattern = "HH:mm") @Schema(type = "string", example = "13:00") LocalTime hasta) {
    }

    // Un turno o un bloqueo. inicio y fin en hora del local; un bloqueo puede abarcar varios días.
    record TurnoDeAgenda(UUID id, UUID profesionalId, String tipo, String estado,
            @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm") @Schema(type = "string", example = "2026-11-17T10:00") LocalDateTime inicio,
            @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm") @Schema(type = "string", example = "2026-11-17T10:45") LocalDateTime fin,
            @Schema(types = {"string", "null"}) String clienteNombre,
            @Schema(types = {"string", "null"}) String clienteTelefono,
            List<String> servicios,
            @Schema(types = {"string", "null"}) String formaPago,
            @Schema(types = {"string", "null"}) String nota,
            @Schema(types = {"string", "null"}) String motivo) {
    }

    // desde y hasta: el primer y el último día pedidos.
    record Agenda(LocalDate desde, LocalDate hasta, List<ProfesionalDeAgenda> profesionales,
            List<FranjaDeAgenda> franjas, List<TurnoDeAgenda> turnos) {
    }

    Agenda ver(Local local, UsuarioSesion usuario, LocalDate desde, int dias, String profesionalPedido) {
        if (dias != 1 && dias != 7) {
            throw new SlotlyException(HttpStatus.BAD_REQUEST, "VALIDACION", "La agenda se pide de 1 o de 7 días.");
        }
        List<Profesional> quienes = deQuien(local, usuario, profesionalPedido);
        List<UUID> ids = quienes.stream().map(Profesional::getId).toList();
        ZoneId zona = ZoneId.of(local.getZonaHoraria());
        LocalDate hasta = desde.plusDays(dias - 1);

        return new Agenda(desde, hasta,
                quienes.stream().map(p -> new ProfesionalDeAgenda(p.getId(), p.getNombre())).toList(),
                franjas(local, ids, desde, dias),
                turnos(local, ids, zona, desde.atStartOfDay(zona).toInstant(), hasta.plusDays(1).atStartOfDay(zona).toInstant()));
    }

    // De quién es la agenda que se pide. Un profesional ve solo la suya (HU-17 CA3). El admin ve la de todos o la
    // de uno, siempre de su local: un profesional de otro local "no existe" (HU-18 CA1 y CA2).
    private List<Profesional> deQuien(Local local, UsuarioSesion usuario, String pedido) {
        if (!usuario.esAdmin()) {
            if (pedido != null && !pedido.equals(usuario.profesionalId().toString())) {
                throw new SlotlyException(HttpStatus.FORBIDDEN, "SIN_PERMISO", "Solo podés ver tu agenda.");
            }
            return List.of(buscar(local, usuario.profesionalId()));
        }
        if (pedido == null || pedido.equals(TODOS)) {
            return profesionales.findByLocalIdAndActivoTrueOrderByOrdenAscNombreAsc(local.getId());
        }
        UUID id;
        try {
            id = UUID.fromString(pedido);
        } catch (IllegalArgumentException e) {
            throw noEncontrado();
        }
        return List.of(buscar(local, id));
    }

    private Profesional buscar(Local local, UUID id) {
        return profesionales.findById(id)
                .filter(p -> p.getLocalId().equals(local.getId()))
                .orElseThrow(AgendaService::noEncontrado);
    }

    private static SlotlyException noEncontrado() {
        return new SlotlyException(HttpStatus.NOT_FOUND, "PROFESIONAL_NO_ENCONTRADO", "No existe ese profesional.");
    }

    // El horario semanal llevado a cada día pedido (dia_semana: 0 domingo a 6 sábado).
    private List<FranjaDeAgenda> franjas(Local local, List<UUID> ids, LocalDate desde, int dias) {
        if (ids.isEmpty()) {
            return List.of();
        }
        List<Horario> semanal = horarios.findByLocalIdAndProfesionalIdIn(local.getId(), ids);
        List<FranjaDeAgenda> franjas = new ArrayList<>();
        for (int i = 0; i < dias; i++) {
            LocalDate fecha = desde.plusDays(i);
            int diaSemana = fecha.getDayOfWeek().getValue() % 7;
            semanal.stream()
                    .filter(h -> h.getDiaSemana() == diaSemana)
                    .map(h -> new FranjaDeAgenda(h.getProfesionalId(), fecha, h.getHoraInicio(), h.getHoraFin()))
                    .forEach(franjas::add);
        }
        franjas.sort(Comparator.comparing(FranjaDeAgenda::fecha).thenComparing(FranjaDeAgenda::desde));
        return franjas;
    }

    // Turnos y bloqueos que se cruzan con el rango, con su cliente y sus servicios, ordenados por hora (HU-17 CA1).
    // No se muestran los apartados (una reserva a medio hacer), los vencidos ni los cancelados: ese horario está libre.
    private List<TurnoDeAgenda> turnos(Local local, List<UUID> ids, ZoneId zona, Instant desde, Instant hasta) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return jdbc.query("""
                SELECT t.id, t.profesional_id, t.tipo, t.estado, t.inicio, t.fin, t.forma_pago, t.nota, t.motivo,
                       c.nombre AS cliente_nombre, c.telefono AS cliente_telefono,
                       ARRAY(SELECT s.nombre FROM turno_servicio ts
                             JOIN servicio s ON s.local_id = ts.local_id AND s.id = ts.servicio_id
                             WHERE ts.turno_id = t.id ORDER BY s.orden, s.nombre) AS servicios
                FROM turno t
                LEFT JOIN cliente c ON c.local_id = t.local_id AND c.id = t.cliente_id
                WHERE t.local_id = ?
                  AND t.profesional_id = ANY (?)
                  AND t.estado IN ('confirmado', 'completado', 'ausente')
                  AND t.inicio < ?
                  AND t.fin > ?
                ORDER BY t.inicio, t.profesional_id
                """,
                (fila, n) -> turno(fila, zona),
                local.getId(), ids.toArray(UUID[]::new), Timestamp.from(hasta), Timestamp.from(desde));
    }

    private static TurnoDeAgenda turno(ResultSet fila, ZoneId zona) throws SQLException {
        Array servicios = fila.getArray("servicios");
        return new TurnoDeAgenda(
                fila.getObject("id", UUID.class),
                fila.getObject("profesional_id", UUID.class),
                fila.getString("tipo"),
                fila.getString("estado"),
                LocalDateTime.ofInstant(fila.getTimestamp("inicio").toInstant(), zona),
                LocalDateTime.ofInstant(fila.getTimestamp("fin").toInstant(), zona),
                fila.getString("cliente_nombre"),
                fila.getString("cliente_telefono"),
                Arrays.asList((String[]) servicios.getArray()),
                fila.getString("forma_pago"),
                fila.getString("nota"),
                fila.getString("motivo"));
    }
}
