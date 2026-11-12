package ar.com.slotly.reserva;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.annotation.JsonFormat;

import io.swagger.v3.oas.annotations.media.Schema;

import ar.com.slotly.cliente.Telefonos;
import ar.com.slotly.error.SlotlyException;
import ar.com.slotly.local.Local;

// Confirma un apartado con los datos del cliente (HU-10) y muestra un turno con su token ("Mi turno").
// Todo pasa en una transacción: el apartado y el cliente se bloquean (SELECT … FOR UPDATE) para que dos pedidos a la
// vez no confirmen dos veces el mismo turno ni pasen los dos el máximo de turnos del teléfono (HU-14 CA5, doc 03).
@Service
public class ConfirmacionService {

    // Cuánto dura el link de "Mi turno" después de que termina el turno.
    static final Duration TOKEN_DESPUES_DEL_TURNO = Duration.ofDays(30);

    private final JdbcTemplate jdbc;
    private final Clock reloj;

    ConfirmacionService(JdbcTemplate jdbc, Clock reloj) {
        this.jdbc = jdbc;
        this.reloj = reloj;
    }

    // Los datos que deja el cliente (ya validados por el controlador).
    public record DatosDelCliente(String nombre, String telefono, String email, String formaPago) {
    }

    // Un turno tal como lo ve el cliente: la pantalla de confirmación y "Mi turno" muestran esto.
    // transferencia: alias, CBU y titular del local, solo si eligió pagar por transferencia (HU-10 CA5).
    // yaPaso: el turno ya empezó. puedeCambiar: el cliente todavía puede cancelarlo o reprogramarlo solo (está
    // confirmado y faltan más horas que las que pide el local, HU-20 CA2); si no, tiene que escribirle al local.
    public record TurnoPublico(UUID id, String estado, LocalDate fecha, @JsonFormat(pattern = "HH:mm") LocalTime hora,
            String profesionalNombre, List<ServicioDelTurno> servicios, int duracionMin, int precioTotal,
            @Schema(types = {"string", "null"}) String formaPago,
            @Schema(types = {"object", "null"}) DatosTransferencia transferencia,
            boolean yaPaso, boolean puedeCambiar) {
    }

    public record ServicioDelTurno(String nombre, int duracionMin, int precio) {
    }

    public record DatosTransferencia(@Schema(types = {"string", "null"}) String alias,
            @Schema(types = {"string", "null"}) String cbu, @Schema(types = {"string", "null"}) String titular) {
    }

    @Transactional
    public TurnoPublico confirmar(Local local, UUID id, String token, DatosDelCliente datos) {
        Instant ahora = reloj.instant();
        Map<String, Object> turno = bloquearTurno(local, id, token);

        // HU-10 CA6 y CA7: tocar dos veces "Confirmar" (o reintentar) no crea otro turno: devuelve el mismo.
        if ("confirmado".equals(turno.get("estado"))) {
            return ver(local, id);
        }
        Instant venceEn = turno.get("vence_en") == null ? null : ((Timestamp) turno.get("vence_en")).toInstant();
        if (!"apartado".equals(turno.get("estado")) || venceEn == null || !venceEn.isAfter(ahora)) {
            // HU-11 CA2
            throw new SlotlyException(HttpStatus.CONFLICT, "APARTADO_VENCIDO",
                    "Se venció el tiempo para confirmar. Elegí la hora de nuevo.");
        }

        UUID clienteId = guardarCliente(local, datos, ahora);
        // HU-14: con el cliente bloqueado, dos confirmaciones a la vez del mismo teléfono se cuentan de a una.
        Integer futuros = jdbc.queryForObject("""
                SELECT count(*) FROM turno
                WHERE local_id = ? AND cliente_id = ? AND estado = 'confirmado' AND inicio > ?
                """, Integer.class, local.getId(), clienteId, Timestamp.from(ahora));
        if (futuros != null && futuros >= local.getMaxTurnosFuturos()) {
            throw new SlotlyException(HttpStatus.CONFLICT, "MAXIMO_TURNOS",
                    "Ya tenés " + local.getMaxTurnosFuturos() + " turnos reservados. Podés verlos en \"Mi turno\".");
        }

        Instant fin = ((Timestamp) turno.get("fin")).toInstant();
        jdbc.update("""
                UPDATE turno SET estado = 'confirmado', cliente_id = ?, forma_pago = ?, vence_en = NULL, token_vence_en = ?
                WHERE id = ?
                """, clienteId, datos.formaPago(), Timestamp.from(fin.plus(TOKEN_DESPUES_DEL_TURNO)), id);
        return ver(local, id);
    }

    // "Mi turno": el turno con ese id y ese token (HU-10 CA7 y CA9, HU-19).
    // Token que no corresponde, turno inexistente o de otro local: 404, lo mismo en los tres casos (HU-19 CA2 y CA5).
    // Token correcto pero vencido (30 días después del turno): 410, para poder decir "Este link venció" (CA3).
    @Transactional(readOnly = true)
    public TurnoPublico verConToken(Local local, UUID id, String token) {
        List<Timestamp> venceEn = jdbc.queryForList("""
                SELECT token_vence_en FROM turno WHERE id = ? AND local_id = ? AND token_hash = ?
                """, Timestamp.class, id, local.getId(), TokenSecreto.hash(token));
        if (venceEn.isEmpty() || venceEn.get(0) == null) {
            throw turnoNoEncontrado();
        }
        if (!venceEn.get(0).toInstant().isAfter(reloj.instant())) {
            throw new SlotlyException(HttpStatus.GONE, "LINK_VENCIDO", "Este link venció.");
        }
        return ver(local, id);
    }

    // Bloquea la fila del turno hasta el final de la transacción. Si no existe, es de otro local o el token no
    // corresponde, responde lo mismo: 404, sin confirmar que existe.
    private Map<String, Object> bloquearTurno(Local local, UUID id, String token) {
        try {
            return jdbc.queryForMap("""
                    SELECT estado, vence_en, fin FROM turno
                    WHERE id = ? AND local_id = ? AND token_hash = ?
                    FOR UPDATE
                    """, id, local.getId(), TokenSecreto.hash(token));
        } catch (EmptyResultDataAccessException e) {
            throw turnoNoEncontrado();
        }
    }

    // El cliente se identifica por el teléfono normalizado dentro del local (HU-10 CA3). Si ya existe, se actualizan
    // su nombre, su mail (si dejó uno) y la fecha del consentimiento; la fila queda bloqueada hasta el final.
    private UUID guardarCliente(Local local, DatosDelCliente datos, Instant ahora) {
        String telefono = Telefonos.normalizar(datos.telefono())
                .orElseThrow(() -> new IllegalStateException("El teléfono ya fue validado"));
        String email = datos.email() == null || datos.email().isBlank() ? null : datos.email().trim();
        UUID clienteId = jdbc.queryForObject("""
                INSERT INTO cliente (local_id, nombre, telefono, email, consentimiento_en)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (local_id, telefono) DO UPDATE
                    SET nombre = EXCLUDED.nombre,
                        email = COALESCE(EXCLUDED.email, cliente.email),
                        consentimiento_en = EXCLUDED.consentimiento_en
                RETURNING id
                """, UUID.class, local.getId(), datos.nombre().trim(), telefono, email, Timestamp.from(ahora));
        jdbc.queryForList("SELECT id FROM cliente WHERE id = ? FOR UPDATE", clienteId);
        return clienteId;
    }

    // El turno tal como lo ve el cliente. Lo usa también MiTurnoService después de cancelar o reprogramar.
    TurnoPublico ver(Local local, UUID id) {
        Map<String, Object> turno = jdbc.queryForMap("""
                SELECT t.estado, t.inicio, t.forma_pago, p.nombre AS profesional
                FROM turno t JOIN profesional p ON p.local_id = t.local_id AND p.id = t.profesional_id
                WHERE t.id = ? AND t.local_id = ?
                """, id, local.getId());
        List<ServicioDelTurno> servicios = jdbc.query("""
                SELECT s.nombre, ts.duracion_min, ts.precio
                FROM turno_servicio ts JOIN servicio s ON s.local_id = ts.local_id AND s.id = ts.servicio_id
                WHERE ts.turno_id = ? AND ts.local_id = ?
                ORDER BY s.orden, s.nombre
                """, (fila, n) -> new ServicioDelTurno(fila.getString("nombre"), fila.getInt("duracion_min"),
                fila.getInt("precio")), id, local.getId());

        Instant comienzo = ((Timestamp) turno.get("inicio")).toInstant();
        ZonedDateTime inicio = comienzo.atZone(ZoneId.of(local.getZonaHoraria()));
        String estado = (String) turno.get("estado");
        Instant ahora = reloj.instant();
        boolean yaPaso = !ahora.isBefore(comienzo);
        boolean puedeCambiar = "confirmado".equals(estado)
                && ahora.isBefore(comienzo.minus(Duration.ofHours(local.getCancelarHastaHoras())));
        String formaPago = (String) turno.get("forma_pago");
        DatosTransferencia transferencia = "transferencia".equals(formaPago)
                ? new DatosTransferencia(local.getAlias(), local.getCbu(), local.getTitularCuenta())
                : null;
        return new TurnoPublico(id, estado, inicio.toLocalDate(), inicio.toLocalTime(),
                (String) turno.get("profesional"), servicios,
                servicios.stream().mapToInt(ServicioDelTurno::duracionMin).sum(),
                servicios.stream().mapToInt(ServicioDelTurno::precio).sum(), formaPago, transferencia,
                yaPaso, puedeCambiar);
    }

    static SlotlyException turnoNoEncontrado() {
        return new SlotlyException(HttpStatus.NOT_FOUND, "TURNO_NO_ENCONTRADO", "No encontramos ese turno.");
    }
}
