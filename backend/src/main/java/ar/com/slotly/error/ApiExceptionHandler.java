package ar.com.slotly.error;

import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

// Convierte las excepciones de todos los controladores en ProblemDetail con "codigo" (doc 00).
// Hereda de ResponseEntityExceptionHandler para que los errores propios de Spring (404, 405...) mantengan su estado.
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    // Un campo con error, para que el front lo marque junto al campo (doc 00: "errores con el detalle por campo").
    public record ErrorDeCampo(String campo, String mensaje) {
    }

    @ExceptionHandler(SlotlyException.class)
    public ResponseEntity<ProblemDetail> manejarSlotly(SlotlyException e) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(e.getEstado(), e.getMessage());
        problema.setProperty("codigo", e.getCodigo());
        ResponseEntity.BodyBuilder respuesta = ResponseEntity.status(e.getEstado());
        // Un 429 dice cuándo se puede volver a probar (HU-16 CA3).
        if (e instanceof DemasiadosIntentosException demasiados) {
            respuesta.header(HttpHeaders.RETRY_AFTER, String.valueOf(demasiados.getSegundosParaReintentar()));
        }
        return respuesta.body(problema);
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

    // Un cuerpo con campos inválidos (@Valid): 400 VALIDACION con la lista de campos y su mensaje (HU-10 CA4).
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Revisá los datos marcados.");
        List<ErrorDeCampo> errores = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> new ErrorDeCampo(e.getField(), e.getDefaultMessage()))
                .toList();
        problema.setProperty("errores", errores);
        return handleExceptionInternal(ex, problema, headers, HttpStatus.BAD_REQUEST, request);
    }

    // Un cuerpo que no se puede leer: JSON roto, un campo que no existe en el contrato o con el tipo equivocado.
    // Se rechaza con 400 en vez de ignorarlo en silencio (HU-15 CA3).
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "El pedido tiene datos que no corresponden o con un formato inválido.");
        return handleExceptionInternal(ex, problema, headers, HttpStatus.BAD_REQUEST, request);
    }

    // Todos los 400 propios de Spring (parámetro faltante, tipo equivocado...) llevan codigo VALIDACION,
    // así el front decide siempre por el código (doc 00).
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest request) {
        ResponseEntity<Object> respuesta = super.handleExceptionInternal(ex, body, headers, statusCode, request);
        if (respuesta != null && statusCode.value() == 400 && respuesta.getBody() instanceof ProblemDetail problema
                && (problema.getProperties() == null || !problema.getProperties().containsKey("codigo"))) {
            problema.setProperty("codigo", "VALIDACION");
        }
        return respuesta;
    }
}
