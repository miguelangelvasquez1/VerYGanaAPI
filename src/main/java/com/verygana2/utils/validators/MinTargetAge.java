package com.verygana2.utils.validators;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * La edad debe ser al menos la mínima configurada en {@code app.targeting.min-age}.
 * Reemplaza a {@code @Min(<literal>)}, que no admite valores de configuración.
 * {@code null} es válido: combínese con {@code @NotNull} cuando el campo sea obligatorio.
 * El mensaje puede usar {@code {minAge}} para mostrar el valor configurado.
 */
@Documented
@Constraint(validatedBy = MinTargetAgeValidator.class)
@Target({ ElementType.FIELD, ElementType.PARAMETER })
@Retention(RetentionPolicy.RUNTIME)
public @interface MinTargetAge {

    String message() default "Age must be at least {minAge}";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
