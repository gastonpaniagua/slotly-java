package ar.com.slotly.reserva;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import ar.com.slotly.cliente.Telefono;
import ar.com.slotly.local.Local;
import ar.com.slotly.local.LocalResolver;
import ar.com.slotly.reserva.CodigoService.Acceso;
import ar.com.slotly.reserva.ConfirmacionService.TurnoPublico;

// Entrar con el teléfono y un código (HU-22) y ver todos los turnos del cliente en el local (HU-22 CA3, HU-23).
@RestController
class MisTurnosController {

    private final LocalResolver localResolver;
    private final CodigoService codigos;
    private final ConfirmacionService confirmaciones;
    private final JdbcTemplate jdbc;

    MisTurnosController(LocalResolver localResolver, CodigoService codigos, ConfirmacionService confirmaciones,
            JdbcTemplate jdbc) {
        this.localResolver = localResolver;
        this.codigos = codigos;
        this.confirmaciones = confirmaciones;
        this.jdbc = jdbc;
    }

    record PedirCodigoRequest(@NotBlank(message = "Ingresá tu celular.") @Telefono String telefono) {
    }

    // La misma respuesta para cualquier teléfono (CA2): solo cuánto dura el código.
    record CodigoPedido(int vigenciaMinutos) {
    }

    record VerificarCodigoRequest(
            @NotBlank(message = "Ingresá tu celular.") @Telefono String telefono,
            @NotBlank(message = "Ingresá el código.")
            @Pattern(regexp = "\\s*\\d{6}\\s*", message = "El código tiene 6 números.") String codigo) {
    }

    // Un turno de la lista, con lo que hace falta para "Reservar lo mismo" (HU-23): profesional y servicios.
    record TurnoDelCliente(TurnoPublico turno, UUID profesionalId, List<UUID> servicioIds) {
    }

    // proximos: los confirmados que todavía no empezaron, del más cercano al más lejano.
    // historial: los demás (pasados y cancelados), del más nuevo al más viejo (HU-23 CA1).
    record MisTurnos(List<TurnoDelCliente> proximos, List<TurnoDelCliente> historial) {
    }

    @PostMapping("/api/publico/codigos")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Pide un código para entrar con el teléfono. Misma respuesta tenga o no turnos (HU-22)")
    CodigoPedido pedir(@Parameter(hidden = true) @RequestHeader(HttpHeaders.HOST) String host,
            @Valid @RequestBody PedirCodigoRequest pedido) {
        codigos.pedir(localResolver.resolver(host), pedido.telefono());
        return new CodigoPedido((int) CodigoService.VIGENCIA.toMinutes());
    }

    @PostMapping("/api/publico/codigos/verificar")
    @Operation(summary = "Verifica el código y entrega el acceso del cliente, de 30 días (HU-22)")
    Acceso verificar(@Parameter(hidden = true) @RequestHeader(HttpHeaders.HOST) String host,
            @Valid @RequestBody VerificarCodigoRequest pedido) {
        return codigos.verificar(localResolver.resolver(host), pedido.telefono(), pedido.codigo());
    }

    @GetMapping("/api/publico/mis-turnos")
    @Operation(summary = "Los turnos del cliente en el local: próximos e historial (HU-22 CA3, HU-23 CA1)")
    MisTurnos misTurnos(@Parameter(hidden = true) @RequestHeader(HttpHeaders.HOST) String host,
            @RequestHeader(Credencial.HEADER_ACCESO) String acceso) {
        Local local = localResolver.resolver(host);
        UUID cliente = codigos.clienteDelAcceso(local, acceso);
        // Un turno reprogramado no se muestra: ya está su reemplazo (reprogramado_de_id apunta a él).
        List<UUID> ids = jdbc.queryForList("""
                SELECT t.id FROM turno t
                WHERE t.local_id = ? AND t.cliente_id = ? AND t.tipo IN ('reserva', 'manual')
                  AND t.estado IN ('confirmado', 'completado', 'ausente', 'cancelado')
                  AND NOT EXISTS (SELECT 1 FROM turno n WHERE n.local_id = t.local_id AND n.reprogramado_de_id = t.id)
                ORDER BY t.inicio
                """, UUID.class, local.getId(), cliente);
        List<TurnoDelCliente> turnos = ids.stream().map(id -> delCliente(local, id)).toList();
        List<TurnoDelCliente> proximos = turnos.stream()
                .filter(t -> "confirmado".equals(t.turno().estado()) && !t.turno().yaPaso())
                .toList();
        List<TurnoDelCliente> historial = turnos.stream()
                .filter(t -> !proximos.contains(t))
                .toList()
                .reversed();
        return new MisTurnos(proximos, historial);
    }

    private TurnoDelCliente delCliente(Local local, UUID id) {
        UUID profesional = jdbc.queryForObject("SELECT profesional_id FROM turno WHERE id = ?", UUID.class, id);
        List<UUID> servicios = jdbc.queryForList("""
                SELECT ts.servicio_id FROM turno_servicio ts
                JOIN servicio s ON s.local_id = ts.local_id AND s.id = ts.servicio_id
                WHERE ts.turno_id = ? ORDER BY s.orden, s.nombre
                """, UUID.class, id);
        return new TurnoDelCliente(confirmaciones.ver(local, id), profesional, servicios);
    }
}
