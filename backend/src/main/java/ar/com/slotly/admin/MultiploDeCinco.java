package ar.com.slotly.admin;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

// Duraciones en múltiplos de 5 minutos (HU-24 CA1, la misma regla que la base). Va sobre el campo, así el error
// sale con el nombre del campo y el panel lo muestra junto a él. Un valor vacío lo controla @NotNull, no esta.
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = MultiploDeCinco.Validador.class)
public @interface MultiploDeCinco {

    String message() default "Tiene que ser múltiplo de 5 minutos.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validador implements ConstraintValidator<MultiploDeCinco, Integer> {
        @Override
        public boolean isValid(Integer valor, ConstraintValidatorContext contexto) {
            return valor == null || valor % 5 == 0;
        }
    }
}
