package ar.com.slotly.error;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;

// Error esperado de la API: lleva el estado HTTP y el código que lee el front (doc 00).
// El mensaje (el de RuntimeException) es el "detail" del ProblemDetail: texto para personas.
public class SlotlyException extends RuntimeException {

    private final HttpStatus estado;
    private final String codigo;
    // Datos extra para el front, además de codigo y detail. Por ejemplo, la lista de turnos que impiden un
    // bloqueo (HU-27 CA2). Salen como propiedades del ProblemDetail.
    private final Map<String, Object> propiedades = new LinkedHashMap<>();

    public SlotlyException(HttpStatus estado, String codigo, String detalle) {
        super(detalle);
        this.estado = estado;
        this.codigo = codigo;
    }

    public SlotlyException con(String nombre, Object valor) {
        propiedades.put(nombre, valor);
        return this;
    }

    public Map<String, Object> getPropiedades() {
        return propiedades;
    }

    public HttpStatus getEstado() {
        return estado;
    }

    public String getCodigo() {
        return codigo;
    }
}
