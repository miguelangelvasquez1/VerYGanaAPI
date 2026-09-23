package com.verygana2.services.interfaces.eligibility;

import com.verygana2.dtos.user.ConsumerRegisterDTO;

public interface ConsumerEligibilityService {

    /**
     * Valida mayoría de edad, declaración explícita de edad y aceptación de
     * la versión vigente de los términos y condiciones para un registro
     * NUEVO de consumidor. Lanza RegistrationRejectedException si no es
     * elegible. No valida cuenta única (personId) todavía — eso llega con
     * DUPLICATE_PERSON_ID en un paso posterior.
     */
    void assertEligible(ConsumerRegisterDTO dto);
}
