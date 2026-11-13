package ar.com.slotly.reserva;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

// El envío simulado: en vez de mandar un mensaje, escribe el código en la terminal de la API para poder probar
// el flujo en desarrollo. Nunca va a producción: ahí se reemplaza por el envío real (Etapa 8).
@Component
class EnvioDeCodigosSimulado implements EnvioDeCodigos {

    private static final Logger LOG = LoggerFactory.getLogger(EnvioDeCodigosSimulado.class);

    @Override
    public void enviar(String telefono, String codigo) {
        LOG.info("[SIMULADO] Código para {}: {}", telefono, codigo);
    }
}
