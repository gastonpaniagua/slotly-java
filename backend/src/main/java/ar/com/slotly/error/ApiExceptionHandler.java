package ar.com.slotly.error;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

// Convierte las excepciones de todos los controladores en ProblemDetail con "codigo" (doc 00).
// Hereda de ResponseEntityExceptionHandler para que los errores propios de Spring (404, 405...) mantengan su estado.
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(SlotlyException.class)
    public ProblemDetail manejarSlotly(SlotlyException e) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(e.getEstado(), e.getMessage());
        problema.setProperty("codigo", e.getCodigo());
        return problema;
    }

    // Lo que nadie más maneja: el detalle completo va al log y al cliente solo un texto genérico.
    @ExceptionHandler(Exception.class)
    public ProblemDetail manejarInesperado(Exception e) {
        logger.error("Error inesperado", e);
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "Ocurrió un error inesperado. Probá de nuevo en unos minutos.");
        problema.setProperty("codigo", "ERROR_INTERNO");
        return problema;
    }
}
