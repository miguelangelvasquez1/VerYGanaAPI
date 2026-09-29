package com.verygana2.utils.validators;

import org.hibernate.validator.constraintvalidation.HibernateConstraintValidatorContext;

import com.verygana2.config.TargetingProperties;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import lombok.RequiredArgsConstructor;

/** Spring instancia este validador e inyecta {@link TargetingProperties}. */
@RequiredArgsConstructor
public class MinTargetAgeValidator implements ConstraintValidator<MinTargetAge, Integer> {

    private final TargetingProperties targetingProperties;

    @Override
    public boolean isValid(Integer value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        int minAge = targetingProperties.getMinAge();
        if (value >= minAge) {
            return true;
        }
        context.unwrap(HibernateConstraintValidatorContext.class).addMessageParameter("minAge", minAge);
        return false;
    }
}
