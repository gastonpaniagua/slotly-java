package ar.com.slotly.admin;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;

import ar.com.slotly.local.Local;
import ar.com.slotly.local.LocalResolver;

// Un vistazo a cómo viene el local (HU-33): turnos de hoy y de la semana (de lunes a domingo, en hora del local),
// ingresos estimados y faltas. Se calcula de los turnos en cada pedido (doc 03: nada se guarda como contador).
@RestController
class ResumenAdminController {

    private final LocalResolver localResolver;
    private final JdbcTemplate jdbc;
    private final Clock reloj;

    ResumenAdminController(LocalResolver localResolver, JdbcTemplate jdbc, Clock reloj) {
        this.localResolver = localResolver;
        this.jdbc = jdbc;
        this.reloj = reloj;
    }

    // ingresosSemana: suma de los precios copiados de los turnos de la semana que no se cancelaron.
    record Resumen(LocalDate hoy, LocalDate semanaDesde, LocalDate semanaHasta, int turnosHoy, int turnosSemana,
            long ingresosSemana, int faltasSemana) {
    }

    @GetMapping("/api/admin/resumen")
    @Operation(summary = "Turnos de hoy y de la semana, ingresos estimados y faltas (HU-33)")
    Resumen ver(@Parameter(hidden = true) @RequestHeader(HttpHeaders.HOST) String host) {
        Local local = localResolver.resolver(host);
        ZoneId zona = ZoneId.of(local.getZonaHoraria());
        LocalDate hoy = LocalDate.ofInstant(reloj.instant(), zona);
        LocalDate lunes = hoy.with(DayOfWeek.MONDAY);
        LocalDate domingo = lunes.plusDays(6);

        // Turnos de clientes (no bloqueos) que cuentan: los no cancelados. Los apartados y vencidos no son turnos.
        Map<String, Object> cuentas = jdbc.queryForMap("""
                SELECT count(*) FILTER (WHERE t.inicio >= ? AND t.inicio < ?) AS hoy,
                       count(*) AS semana,
                       count(*) FILTER (WHERE t.estado = 'ausente') AS faltas,
                       coalesce(sum((SELECT sum(ts.precio) FROM turno_servicio ts WHERE ts.turno_id = t.id)), 0) AS ingresos
                FROM turno t
                WHERE t.local_id = ? AND t.tipo <> 'bloqueo' AND t.estado IN ('confirmado', 'completado', 'ausente')
                  AND t.inicio >= ? AND t.inicio < ?
                """,
                inicioDe(hoy, zona), inicioDe(hoy.plusDays(1), zona),
                local.getId(), inicioDe(lunes, zona), inicioDe(domingo.plusDays(1), zona));
        return new Resumen(hoy, lunes, domingo, ((Number) cuentas.get("hoy")).intValue(),
                ((Number) cuentas.get("semana")).intValue(), ((Number) cuentas.get("ingresos")).longValue(),
                ((Number) cuentas.get("faltas")).intValue());
    }

    private static Timestamp inicioDe(LocalDate dia, ZoneId zona) {
        return Timestamp.from(dia.atStartOfDay(zona).toInstant());
    }
}
