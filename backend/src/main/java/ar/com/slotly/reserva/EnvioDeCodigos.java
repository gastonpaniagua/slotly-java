package ar.com.slotly.reserva;

// Por dónde le llega el código al cliente (HU-22 CA1). En la v1 es un envío simulado (doc 00, decisión 9); el
// WhatsApp real (Etapa 8) va a ser otra implementación de esta misma interfaz, sin tocar quien la usa.
public interface EnvioDeCodigos {

    void enviar(String telefono, String codigo);
}
