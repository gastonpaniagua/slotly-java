package ar.com.slotly.mensaje;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

// El envío simulado de la v1: en vez de mandar un WhatsApp, escribe el mensaje en la terminal de la API para poder
// probar todo el flujo en desarrollo.
@Component
class EnvioDeMensajesSimulado implements EnvioDeMensajes {

    private static final Logger LOG = LoggerFactory.getLogger(EnvioDeMensajesSimulado.class);

    @Override
    public void enviar(String telefono, String texto) {
        LOG.info("[SIMULADO] WhatsApp a {}: {}", telefono, texto);
    }
}
