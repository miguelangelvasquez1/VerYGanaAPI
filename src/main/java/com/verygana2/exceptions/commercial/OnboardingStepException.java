package com.verygana2.exceptions.commercial;

import com.verygana2.exceptions.BusinessException;

/** Se lanza cuando se intenta completar un paso del onboarding comercial fuera de orden. */
public class OnboardingStepException extends BusinessException {
    public OnboardingStepException(String message) {
        super(message);
    }
}
