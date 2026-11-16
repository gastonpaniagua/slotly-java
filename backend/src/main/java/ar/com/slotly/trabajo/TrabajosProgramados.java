package ar.com.slotly.trabajo;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import ar.com.slotly.mensaje.TrabajoDeMensajes;

// Los trabajos en segundo plano (D2): Spring los corre cada tanto, sin servicios aparte. Cada uno espera a que
// termine la vuelta anterior (fixedDelay). En los tests están apagados (slotly.trabajos.activos=false): cada test
// llama al trabajo directo, con el reloj fijado, sin esperar a que se dispare.
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "slotly.trabajos.activos", havingValue = "true", matchIfMissing = true)
class TrabajosProgramados {

    private final TrabajoDeMensajes mensajes;
    private final ApartadosVencidos apartados;

    TrabajosProgramados(TrabajoDeMensajes mensajes, ApartadosVencidos apartados) {
        this.mensajes = mensajes;
        this.apartados = apartados;
    }

    @Scheduled(initialDelayString = "PT10S", fixedDelayString = "PT30S")
    void mandarMensajes() {
        mensajes.enviarPendientes();
    }

    @Scheduled(initialDelayString = "PT10S", fixedDelayString = "PT1M")
    void vencerApartados() {
        apartados.vencer();
    }
}
