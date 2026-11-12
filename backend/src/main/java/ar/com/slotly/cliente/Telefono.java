package ar.com.slotly.cliente;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

// Valida que un campo sea un celular que Telefonos.normalizar entiende (HU-10 CA4).
// Vacío pasa: que sea obligatorio lo dice @NotBlank.
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = Telefono.Validador.class)
public @interface Telefono {

    String message() default "Ingresá un celular válido, por ejemplo 11 2345-6789.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validador implements ConstraintValidator<Telefono, String> {

        @Override
        public boolean isValid(String valor, ConstraintValidatorContext contexto) {
            return valor == null || valor.isBlank() || Telefonos.normalizar(valor).isPresent();
        }
    }
}
