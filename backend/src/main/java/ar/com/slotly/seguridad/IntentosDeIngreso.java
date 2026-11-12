package ar.com.slotly.seguridad;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

// Frena a quien prueba contraseñas (HU-16 CA3): 5 fallos seguidos para el mismo mail bloquean ese mail 15 minutos.
// Igual que LimiteDePedidos: en memoria (un solo servidor en la v1) y con el reloj inyectable para los tests.
@Component
public class IntentosDeIngreso {

    public static final int MAXIMO_FALLOS = 5;
    public static final Duration BLOQUEO = Duration.ofMinutes(15);

    private static final int LIMPIAR_DESDE = 10_000;

    // fallos: los fallos seguidos hasta ahora. bloqueadoHasta: null si no está bloqueado.
    private record Estado(int fallos, Instant bloqueadoHasta) {
    }

    private final Map<String, Estado> porClave = new ConcurrentHashMap<>();
    private final Clock reloj;

    IntentosDeIngreso(Clock reloj) {
        this.reloj = reloj;
    }

    // Si la clave está bloqueada, cuánto falta para que se libere.
    public Optional<Duration> bloqueo(String clave) {
        Estado estado = porClave.get(clave);
        Instant ahora = reloj.instant();
        if (estado == null || estado.bloqueadoHasta() == null || !ahora.isBefore(estado.bloqueadoHasta())) {
            return Optional.empty();
        }
        return Optional.of(Duration.between(ahora, estado.bloqueadoHasta()));
    }

    // Un fallo más. El quinto seguido bloquea; cuando el bloqueo pasa, la cuenta arranca de cero.
    public void registrarFallo(String clave) {
        Instant ahora = reloj.instant();
        if (porClave.size() > LIMPIAR_DESDE) {
            porClave.values().removeIf(e -> e.bloqueadoHasta() != null && !ahora.isBefore(e.bloqueadoHasta()));
        }
        porClave.compute(clave, (c, anterior) -> {
            if (anterior != null && anterior.bloqueadoHasta() != null && ahora.isBefore(anterior.bloqueadoHasta())) {
                return anterior;   // ya bloqueado: el bloqueo no se alarga ni se reinicia
            }
            int fallos = anterior == null || anterior.bloqueadoHasta() != null ? 1 : anterior.fallos() + 1;
            return fallos >= MAXIMO_FALLOS ? new Estado(0, ahora.plus(BLOQUEO)) : new Estado(fallos, null);
        });
    }

    // Entró bien: los fallos anteriores ya no cuentan ("seguidos").
    public void registrarExito(String clave) {
        porClave.remove(clave);
    }

    // Para los tests: cada uno arranca sin fallos.
    public void reiniciar() {
        porClave.clear();
    }
}
