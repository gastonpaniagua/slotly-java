package ar.com.slotly.trabajo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import ar.com.slotly.mensaje.PedidosDeResena;
import ar.com.slotly.mensaje.TrabajoDeMensajes;

// Los trabajos en segundo plano (D2): Spring los corre cada tanto, sin servicios aparte. Cada uno espera a que
// termine la vuelta anterior (fixedDelay). En los tests están apagados (slotly.trabajos.activos=false): cada test
// llama al trabajo directo, con el reloj fijado, sin esperar a que se dispare.
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "slotly.trabajos.activos", havingValue = "true", matchIfMissing = true)
class TrabajosProgramados {

    // Al log, solo las vueltas que hicieron algo: una vuelta vacía cada 30 s no aporta.
    private static final Logger LOG = LoggerFactory.getLogger(TrabajosProgramados.class);

    private final TrabajoDeMensajes mensajes;
    private final ApartadosVencidos apartados;
    private final PedidosDeResena resenas;

    TrabajosProgramados(TrabajoDeMensajes mensajes, ApartadosVencidos apartados, PedidosDeResena resenas) {
        this.mensajes = mensajes;
        this.apartados = apartados;
        this.resenas = resenas;
    }

    @Scheduled(initialDelayString = "PT10S", fixedDelayString = "PT30S")
    void mandarMensajes() {
        int procesados = mensajes.enviarPendientes();
        if (procesados > 0) {
            LOG.info("Mensajes procesados: {}", procesados);
        }
    }

    @Scheduled(initialDelayString = "PT10S", fixedDelayString = "PT1M")
    void vencerApartados() {
        int vencidos = apartados.vencer();
        if (vencidos > 0) {
            LOG.info("Apartados vencidos: {}", vencidos);
        }
    }

    // HU-38: los encola; los manda mandarMensajes en su próxima vuelta.
    @Scheduled(initialDelayString = "PT10S", fixedDelayString = "PT1M")
    void pedirResenas() {
        int encolados = resenas.encolar();
        if (encolados > 0) {
            LOG.info("Pedidos de reseña encolados: {}", encolados);
        }
    }
}
