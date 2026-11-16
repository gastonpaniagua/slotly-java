package ar.com.slotly.mensaje;

// Por dónde le llega un mensaje al cliente (Etapa 8). En la v1 es un envío simulado; el WhatsApp real (PD-08) va a
// ser otra implementación de esta interfaz, sin tocar el trabajo que la usa. Si no se pudo mandar, tira una excepción
// y el trabajo lo reintenta.
public interface EnvioDeMensajes {

    void enviar(String telefono, String texto);
}
