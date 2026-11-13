package ar.com.slotly.error;

import java.util.List;

import org.springframework.http.HttpStatus;

import ar.com.slotly.error.ApiExceptionHandler.ErrorDeCampo;

// Un 400 VALIDACION que se descubre en el servicio (no con anotaciones), con el campo al que corresponde: por
// ejemplo, "ese mail ya tiene un usuario" (HU-25 CA4). Sale igual que los errores de @Valid: con "errores".
public class ValidacionException extends SlotlyException {

    private final List<ErrorDeCampo> errores;

    public ValidacionException(String campo, String mensaje) {
        super(HttpStatus.BAD_REQUEST, "VALIDACION", mensaje);
        this.errores = List.of(new ErrorDeCampo(campo, mensaje));
    }

    public List<ErrorDeCampo> getErrores() {
        return errores;
    }
}
