package ar.com.slotly.seguridad;

import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

// Cuenta pedidos por IP en ventanas de un minuto de reloj y frena al que pase el máximo (HU-15).
// Vive en memoria: alcanza con un solo servidor, que es el caso de la v1. El "ahora" sale del reloj inyectable,
// así los tests prueban "pasó el minuto" sin esperar un minuto de verdad.
@Component
public class LimiteDePedidos {

    public static final int MAXIMO_POR_MINUTO = 10;

    // Cuando hay muchas IPs guardadas, se descartan las de minutos que ya pasaron.
    private static final int LIMPIAR_DESDE = 10_000;

    private record Ventana(long minuto, int pedidos) {
    }

    private final Map<String, Ventana> porIp = new ConcurrentHashMap<>();
    private final Clock reloj;

    LimiteDePedidos(Clock reloj) {
        this.reloj = reloj;
    }

    // Cuenta el pedido y dice si puede pasar. El pedido número 11 del mismo minuto ya no pasa (HU-15 CA1);
    // al minuto siguiente la cuenta arranca de cero (CA2).
    public boolean permitir(String ip) {
        long minuto = reloj.millis() / 60_000;
        if (porIp.size() > LIMPIAR_DESDE) {
            porIp.values().removeIf(v -> v.minuto() < minuto);
        }
        // compute es atómico por IP: dos pedidos a la vez de la misma IP no se cuentan como uno.
        Ventana ventana = porIp.compute(ip, (clave, anterior) ->
                anterior == null || anterior.minuto() != minuto
                        ? new Ventana(minuto, 1)
                        : new Ventana(minuto, anterior.pedidos() + 1));
        return ventana.pedidos() <= MAXIMO_POR_MINUTO;
    }

    // Segundos que faltan para el próximo minuto (header Retry-After del 429).
    public long segundosHastaElProximoMinuto() {
        return 60 - (reloj.millis() / 1000) % 60;
    }

    // Para los tests: cada uno arranca sin pedidos contados.
    public void reiniciar() {
        porIp.clear();
    }
}
