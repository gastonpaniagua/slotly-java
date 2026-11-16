package ar.com.slotly.mensaje;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

// Manda los mensajes pendientes (D2). La tabla mensaje es la cola: cada mensaje se toma con FOR UPDATE SKIP LOCKED
// en su propia transacción, así dos instancias de la API nunca agarran el mismo, y el candado dura hasta que queda
// marcado. El texto se arma recién ahora, con el día y la hora que el turno tenga en este momento.
@Component
public class TrabajoDeMensajes {

    private static final Logger LOG = LoggerFactory.getLogger(TrabajoDeMensajes.class);

    // HU-35 CA3: después del primer intento, hasta 3 reintentos; esperas entre uno y otro.
    static final List<Duration> ESPERAS = List.of(Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(15));
    // Tope por vuelta, para que una vuelta no se haga eterna si se juntaron muchos.
    private static final int MAXIMO_POR_VUELTA = 100;
    private static final Locale ES_AR = Locale.forLanguageTag("es-AR");
    private static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", ES_AR);
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaccion;
    private final EnvioDeMensajes envio;
    private final Clock reloj;

    TrabajoDeMensajes(JdbcTemplate jdbc, PlatformTransactionManager transacciones, EnvioDeMensajes envio, Clock reloj) {
        this.jdbc = jdbc;
        this.transaccion = new TransactionTemplate(transacciones);
        this.envio = envio;
        this.reloj = reloj;
    }

    private record Pendiente(UUID id, String tipo, String link, int intentos, String estadoTurno, Instant inicio,
            String cliente, String telefono, String local, ZoneId zona, String linkResena) {
    }

    // Una vuelta: manda todo lo que ya tocaba. Devuelve cuántos mensajes procesó (enviados, fallados o descartados).
    public int enviarPendientes() {
        int procesados = 0;
        while (procesados < MAXIMO_POR_VUELTA && Boolean.TRUE.equals(transaccion.execute(estado -> procesarUno()))) {
            procesados++;
        }
        return procesados;
    }

    private boolean procesarUno() {
        Instant ahora = reloj.instant();
        List<Pendiente> pendientes = jdbc.query("""
                SELECT m.id, m.tipo, m.link, m.intentos, t.estado AS estado_turno, t.inicio,
                       c.nombre AS cliente, c.telefono, l.nombre AS local, l.zona_horaria, l.link_resena
                FROM mensaje m
                JOIN turno t ON t.local_id = m.local_id AND t.id = m.turno_id
                JOIN local l ON l.id = m.local_id
                LEFT JOIN cliente c ON c.local_id = t.local_id AND c.id = t.cliente_id
                WHERE m.estado = 'pendiente' AND m.enviar_en <= ?
                ORDER BY m.enviar_en, m.id
                LIMIT 1
                FOR UPDATE OF m SKIP LOCKED
                """, (fila, n) -> new Pendiente(fila.getObject("id", UUID.class), fila.getString("tipo"),
                fila.getString("link"), fila.getInt("intentos"), fila.getString("estado_turno"),
                fila.getTimestamp("inicio").toInstant(), fila.getString("cliente"), fila.getString("telefono"),
                fila.getString("local"), ZoneId.of(fila.getString("zona_horaria")), fila.getString("link_resena")),
                Timestamp.from(ahora));
        if (pendientes.isEmpty()) {
            return false;
        }
        Pendiente mensaje = pendientes.get(0);
        Optional<String> texto = redactar(mensaje, ahora);
        if (texto.isEmpty()) {
            terminar(mensaje.id(), "descartado", ahora);
            return true;
        }
        try {
            envio.enviar(mensaje.telefono(), texto.get());
            terminar(mensaje.id(), "enviado", ahora);
        } catch (RuntimeException error) {
            fallo(mensaje, error, ahora);
        }
        return true;
    }

    // El texto de cada tipo, o vacío si a esta altura ya no corresponde mandarlo (se descarta).
    private Optional<String> redactar(Pendiente m, Instant ahora) {
        if (m.telefono() == null) {
            return Optional.empty();
        }
        LocalDateTime inicio = LocalDateTime.ofInstant(m.inicio(), m.zona());
        String dia = inicio.format(DIA);
        String hora = inicio.format(HORA);
        return switch (m.tipo()) {
            case "confirmacion" -> m.link() == null || !m.estadoTurno().equals("confirmado") ? Optional.empty()
                    : Optional.of("Hola, " + m.cliente() + ". Tu turno en " + m.local() + " quedó reservado para el " + dia
                            + " a las " + hora + ". Para verlo, cancelarlo o cambiarlo: " + m.link());
            case "recordatorio" -> m.link() == null || !m.estadoTurno().equals("confirmado") || !m.inicio().isAfter(ahora)
                    ? Optional.empty()
                    : Optional.of("Hola, " + m.cliente() + ". Te recordamos tu turno en " + m.local() + " el " + dia
                            + " a las " + hora + ". Si no podés venir, cancelalo o cambialo desde acá: " + m.link());
            case "resena" -> m.linkResena() == null || !(m.estadoTurno().equals("completado") || m.estadoTurno().equals("confirmado"))
                    ? Optional.empty()
                    : Optional.of("Hola, " + m.cliente() + ". ¡Gracias por venir a " + m.local()
                            + "! Si te gustó, nos ayuda mucho que nos dejes una reseña: " + m.linkResena());
            default -> Optional.empty();
        };
    }

    private void terminar(UUID id, String estado, Instant ahora) {
        jdbc.update("UPDATE mensaje SET estado = ?, link = NULL, terminado_en = ? WHERE id = ?",
                estado, Timestamp.from(ahora), id);
    }

    // Falló el envío: se reintenta más tarde, y si ya se agotaron los reintentos queda 'fallido' (HU-39).
    private void fallo(Pendiente m, RuntimeException error, Instant ahora) {
        int intentos = m.intentos() + 1;
        String detalle = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
        detalle = detalle.length() > 300 ? detalle.substring(0, 300) : detalle;
        LOG.warn("No se pudo mandar el mensaje {} (intento {}): {}", m.id(), intentos, detalle);
        if (intentos > ESPERAS.size()) {
            jdbc.update("UPDATE mensaje SET estado = 'fallido', intentos = ?, ultimo_error = ?, link = NULL, terminado_en = ? WHERE id = ?",
                    intentos, detalle, Timestamp.from(ahora), m.id());
        } else {
            jdbc.update("UPDATE mensaje SET intentos = ?, ultimo_error = ?, enviar_en = ? WHERE id = ?",
                    intentos, detalle, Timestamp.from(ahora.plus(ESPERAS.get(intentos - 1))), m.id());
        }
    }
}
