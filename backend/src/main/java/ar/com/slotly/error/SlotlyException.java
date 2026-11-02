package ar.com.slotly.error;

import org.springframework.http.HttpStatus;

// Error esperado de la API: lleva el estado HTTP y el código que lee el front (doc 00).
// El mensaje (el de RuntimeException) es el "detail" del ProblemDetail: texto para personas.
public class SlotlyException extends RuntimeException {

    private final HttpStatus estado;
    private final String codigo;

    public SlotlyException(HttpStatus estado, String codigo, String detalle) {
        super(detalle);
        this.estado = estado;
        this.codigo = codigo;
    }

    public HttpStatus getEstado() {
        return estado;
    }

    public String getCodigo() {
        return codigo;
    }
}
