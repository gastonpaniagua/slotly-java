package ar.com.slotly.error;

import org.springframework.http.HttpStatus;

// 429 DEMASIADOS_INTENTOS con cuántos segundos esperar: el ApiExceptionHandler los pone en el header Retry-After.
public class DemasiadosIntentosException extends SlotlyException {

    private final long segundosParaReintentar;

    public DemasiadosIntentosException(String detalle, long segundosParaReintentar) {
        super(HttpStatus.TOO_MANY_REQUESTS, "DEMASIADOS_INTENTOS", detalle);
        this.segundosParaReintentar = segundosParaReintentar;
    }

    public long getSegundosParaReintentar() {
        return segundosParaReintentar;
    }
}
