package ar.com.slotly.admin;

import java.sql.Time;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.annotation.JsonFormat;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import ar.com.slotly.admin.ProfesionalesAdminController.TurnoFuturo;
import ar.com.slotly.error.SlotlyException;
import ar.com.slotly.error.ValidacionException;
import ar.com.slotly.local.Local;
import ar.com.slotly.local.LocalResolver;

// El horario semanal de un profesional, con pausas (HU-26): franjas por día en hora del local. Una pausa son dos
// franjas el mismo día (10 a 13 y 16 a 20). La disponibilidad lee estas franjas, así que el cambio vale en el acto.
@RestController
class HorarioAdminController {

    private static final String[] DIAS = { "domingo", "lunes", "martes", "miércoles", "jueves", "viernes", "sábado" };

    private final LocalResolver localResolver;
    private final JdbcTemplate jdbc;
    private final Clock reloj;

    HorarioAdminController(LocalResolver localResolver, JdbcTemplate jdbc, Clock reloj) {
        this.localResolver = localResolver;
        this.jdbc = jdbc;
        this.reloj = reloj;
    }

    // diaSemana: 0 domingo a 6 sábado (como en la base).
    record Franja(
            @NotNull @Min(0) @Max(6) Integer diaSemana,
            @NotNull @JsonFormat(pattern = "HH:mm") @Schema(type = "string", example = "10:00") LocalTime desde,
            @NotNull @JsonFormat(pattern = "HH:mm") @Schema(type = "string", example = "13:00") LocalTime hasta) {
    }

    record HorarioRequest(@NotNull List<@Valid Franja> franjas) {
    }

    // CA3: después de guardar, los turnos por delante que quedaron fuera del horario nuevo. No se cancelan solos.
    record HorarioGuardado(List<Franja> franjas, List<TurnoFuturo> turnosAfuera) {
    }

    @GetMapping("/api/admin/profesionales/{id}/horario")
    @Operation(summary = "El horario semanal de un profesional (HU-26)")
    List<Franja> ver(@Parameter(hidden = true) @RequestHeader(HttpHeaders.HOST) String host, @PathVariable UUID id) {
        Local local = localResolver.resolver(host);
        exigirProfesional(local, id);
        return franjas(local, id);
    }

    // Reemplaza el horario entero de una vez: así nunca queda a medias.
    @PutMapping("/api/admin/profesionales/{id}/horario")
    @Transactional
    @Operation(summary = "Guarda el horario semanal de un profesional y avisa los turnos que quedan afuera (HU-26)")
    HorarioGuardado guardar(@Parameter(hidden = true) @RequestHeader(HttpHeaders.HOST) String host,
            @PathVariable UUID id, @Valid @RequestBody HorarioRequest pedido) {
        Local local = localResolver.resolver(host);
        exigirProfesional(local, id);
        validar(pedido.franjas());

        jdbc.update("DELETE FROM horario WHERE profesional_id = ? AND local_id = ?", id, local.getId());
        for (Franja franja : pedido.franjas()) {
            jdbc.update("""
                    INSERT INTO horario (local_id, profesional_id, dia_semana, hora_inicio, hora_fin) VALUES (?, ?, ?, ?, ?)
                    """, local.getId(), id, franja.diaSemana(), Time.valueOf(franja.desde()), Time.valueOf(franja.hasta()));
        }
        List<Franja> nuevas = franjas(local, id);
        return new HorarioGuardado(nuevas, turnosAfuera(local, id, nuevas));
    }

    // CA2: cada franja termina después de empezar, y dos franjas del mismo día no se pisan (pegadas sí valen:
    // 10 a 13 y 13 a 16). La base igual lo garantiza (V4); acá es para decir cuál está mal.
    private static void validar(List<Franja> franjas) {
        for (Franja franja : franjas) {
            if (!franja.hasta().isAfter(franja.desde())) {
                throw new ValidacionException("franjas", "La franja del " + DIAS[franja.diaSemana()] + " de "
                        + franja.desde() + " a " + franja.hasta() + " termina antes de empezar.");
            }
        }
        List<Franja> ordenadas = franjas.stream()
                .sorted(Comparator.comparing(Franja::diaSemana).thenComparing(Franja::desde))
                .toList();
        for (int i = 1; i < ordenadas.size(); i++) {
            Franja anterior = ordenadas.get(i - 1);
            Franja actual = ordenadas.get(i);
            if (anterior.diaSemana().equals(actual.diaSemana()) && actual.desde().isBefore(anterior.hasta())) {
                throw new ValidacionException("franjas", "El " + DIAS[actual.diaSemana()] + " tiene dos franjas que se pisan: "
                        + anterior.desde() + " a " + anterior.hasta() + " y " + actual.desde() + " a " + actual.hasta() + ".");
            }
        }
    }

    // Los turnos confirmados por delante que no entran enteros en una franja de su día.
    private List<TurnoFuturo> turnosAfuera(Local local, UUID profesional, List<Franja> franjas) {
        ZoneId zona = ZoneId.of(local.getZonaHoraria());
        return jdbc.query("""
                SELECT t.id, t.inicio, t.fin, c.nombre FROM turno t
                LEFT JOIN cliente c ON c.local_id = t.local_id AND c.id = t.cliente_id
                WHERE t.local_id = ? AND t.profesional_id = ? AND t.tipo <> 'bloqueo' AND t.estado = 'confirmado'
                  AND t.inicio > ?
                ORDER BY t.inicio
                """, (fila, n) -> {
                    LocalDateTime inicio = LocalDateTime.ofInstant(fila.getTimestamp("inicio").toInstant(), zona);
                    LocalDateTime fin = LocalDateTime.ofInstant(fila.getTimestamp("fin").toInstant(), zona);
                    int dia = inicio.getDayOfWeek().getValue() % 7;
                    boolean adentro = fin.toLocalDate().equals(inicio.toLocalDate()) && franjas.stream().anyMatch(f ->
                            f.diaSemana() == dia && !inicio.toLocalTime().isBefore(f.desde())
                                    && !fin.toLocalTime().isAfter(f.hasta()));
                    return adentro ? null : new TurnoFuturo(fila.getObject("id", UUID.class), inicio, fila.getString("nombre"));
                }, local.getId(), profesional, Timestamp.from(reloj.instant()))
                .stream().filter(t -> t != null).toList();
    }

    private List<Franja> franjas(Local local, UUID profesional) {
        return jdbc.query("""
                SELECT dia_semana, hora_inicio, hora_fin FROM horario
                WHERE profesional_id = ? AND local_id = ? ORDER BY dia_semana, hora_inicio
                """, (fila, n) -> new Franja(fila.getInt("dia_semana"), fila.getTime("hora_inicio").toLocalTime(),
                fila.getTime("hora_fin").toLocalTime()), profesional, local.getId());
    }

    private void exigirProfesional(Local local, UUID id) {
        Integer existe = jdbc.queryForObject("SELECT count(*) FROM profesional WHERE id = ? AND local_id = ?",
                Integer.class, id, local.getId());
        if (existe == null || existe == 0) {
            throw new SlotlyException(HttpStatus.NOT_FOUND, "PROFESIONAL_NO_ENCONTRADO", "No existe ese profesional.");
        }
    }
}
