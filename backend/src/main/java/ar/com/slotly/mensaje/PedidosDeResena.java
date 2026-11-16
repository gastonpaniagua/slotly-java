package ar.com.slotly.mensaje;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

// HU-38: encola el pedido de reseña de Google para los turnos que terminaron hace 2 horas. Lo manda TrabajoDeMensajes,
// que a esa altura vuelve a mirar que el turno no se haya marcado "no vino" y que el local siga teniendo el link.
@Component
public class PedidosDeResena {

    // CA1: cuánto después del fin del turno.
    static final Duration DESPUES_DEL_TURNO = Duration.ofHours(2);
    // CA2: a un mismo cliente, como mucho un pedido cada 90 días.
    static final Duration ENTRE_PEDIDOS = Duration.ofDays(90);
    // Solo turnos recientes: si un local carga su link hoy, no les llega el pedido a los clientes de hace meses.
    static final Duration HASTA = Duration.ofDays(3);

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaccion;
    private final Clock reloj;

    PedidosDeResena(JdbcTemplate jdbc, PlatformTransactionManager transacciones, Clock reloj) {
        this.jdbc = jdbc;
        this.transaccion = new TransactionTemplate(transacciones);
        this.reloj = reloj;
    }

    private record Candidato(UUID localId, UUID turnoId, UUID clienteId) {
    }

    // Devuelve cuántos pedidos encoló.
    public int encolar() {
        Instant ahora = reloj.instant();
        // CA1 y CA3: "vino" o terminado sin marcar (sigue confirmado); los cancelados y los "no vino" quedan afuera.
        // CA4: el local tiene link de reseña. Del más nuevo al más viejo: si un cliente tuvo dos turnos, se pide por el
        // último.
        List<Candidato> candidatos = jdbc.query("""
                SELECT t.local_id, t.id, t.cliente_id
                FROM turno t
                JOIN local l ON l.id = t.local_id
                JOIN cliente c ON c.local_id = t.local_id AND c.id = t.cliente_id
                WHERE t.tipo <> 'bloqueo' AND t.estado IN ('completado', 'confirmado')
                  AND t.fin <= ? AND t.fin > ?
                  AND l.link_resena IS NOT NULL
                  AND c.telefono IS NOT NULL
                  AND NOT EXISTS (SELECT 1 FROM mensaje m WHERE m.turno_id = t.id AND m.tipo = 'resena')
                ORDER BY t.fin DESC, t.id
                """, (fila, n) -> new Candidato(fila.getObject("local_id", UUID.class), fila.getObject("id", UUID.class),
                fila.getObject("cliente_id", UUID.class)),
                Timestamp.from(ahora.minus(DESPUES_DEL_TURNO)), Timestamp.from(ahora.minus(HASTA)));

        int encolados = 0;
        for (Candidato candidato : candidatos) {
            if (Boolean.TRUE.equals(transaccion.execute(estado -> encolar(candidato, ahora)))) {
                encolados++;
            }
        }
        return encolados;
    }

    // CA2 y CA5: con el cliente bloqueado se mira cuándo se le pidió la última; si corresponde, se encola y se anota.
    // Así, aunque el trabajo corra dos veces a la vez (o el cliente tenga dos turnos), sale un solo pedido.
    private boolean encolar(Candidato candidato, Instant ahora) {
        List<UUID> cliente = jdbc.queryForList("""
                SELECT id FROM cliente
                WHERE id = ? AND local_id = ? AND (ultima_resena_pedida_en IS NULL OR ultima_resena_pedida_en <= ?)
                FOR UPDATE
                """, UUID.class, candidato.clienteId(), candidato.localId(), Timestamp.from(ahora.minus(ENTRE_PEDIDOS)));
        if (cliente.isEmpty()) {
            return false;
        }
        int nuevos = jdbc.update("""
                INSERT INTO mensaje (local_id, turno_id, tipo, enviar_en, creado_en) VALUES (?, ?, 'resena', ?, ?)
                ON CONFLICT (turno_id, tipo) DO NOTHING
                """, candidato.localId(), candidato.turnoId(), Timestamp.from(ahora), Timestamp.from(ahora));
        if (nuevos == 0) {
            return false;
        }
        jdbc.update("UPDATE cliente SET ultima_resena_pedida_en = ? WHERE id = ?", Timestamp.from(ahora), candidato.clienteId());
        return true;
    }
}
