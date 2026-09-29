package com.verygana2.services.eligibility;

import static com.verygana2.models.enums.RegistrationRejectionReason.AGE_DECLARATION_MISSING;
import static com.verygana2.models.enums.RegistrationRejectionReason.INVALID_DATA;
import static com.verygana2.models.enums.RegistrationRejectionReason.MINOR_AGE;
import static com.verygana2.models.enums.RegistrationRejectionReason.TERMS_NOT_ACCEPTED;
import static com.verygana2.utils.ColombiaTime.BOGOTA_ZONE;

import java.time.Clock;
import java.time.LocalDate;
import java.time.Period;

import org.springframework.stereotype.Service;

import com.verygana2.dtos.user.ConsumerRegisterDTO;
import com.verygana2.exceptions.RegistrationRejectedException;
import com.verygana2.models.enums.legal.LegalDocumentType;
import com.verygana2.models.legal.LegalDocument;
import com.verygana2.repositories.legal.LegalDocumentRepository;
import com.verygana2.services.interfaces.eligibility.ConsumerEligibilityService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ConsumerEligibilityServiceImpl implements ConsumerEligibilityService {

    private static final int LEGAL_AGE = 18;

    private final Clock clock;
    private final LegalDocumentRepository legalDocumentRepository;

    @Override
    public void assertEligible(ConsumerRegisterDTO dto) {
        LocalDate birthDate = dto.getBirthDate();
        if (birthDate == null) {
            throw new RegistrationRejectedException(INVALID_DATA, "La fecha de nacimiento es requerida");
        }

        // Edad primero y de forma incondicional: ninguna otra señal del payload
        // (declaración, autorización de terceros, etc.) puede evitar este rechazo.
        if (calculateAge(birthDate) < LEGAL_AGE) {
            throw new RegistrationRejectedException(MINOR_AGE,
                    "Debes ser mayor de edad para registrarte. Ninguna autorización de un tercero habilita el registro de un menor.");
        }

        if (!Boolean.TRUE.equals(dto.getAgeDeclaration())) {
            throw new RegistrationRejectedException(AGE_DECLARATION_MISSING,
                    "Debes declarar explícitamente que eres mayor de edad");
        }

        LegalDocument activeTerms = legalDocumentRepository.findByTypeAndActiveTrue(LegalDocumentType.USERS_TERMS_AND_CONDITIONS)
                .orElseThrow(() -> new IllegalStateException(
                        "No hay una versión activa de " + LegalDocumentType.USERS_TERMS_AND_CONDITIONS + " configurada"));

        if (!Boolean.TRUE.equals(dto.getTermsAccepted()) || !activeTerms.getVersion().equals(dto.getTermsVersion())) {
            throw new RegistrationRejectedException(TERMS_NOT_ACCEPTED,
                    "Debes aceptar la versión vigente de los términos y condiciones (v" + activeTerms.getVersion() + ")");
        }
    }

    private int calculateAge(LocalDate birthDate) {
        LocalDate today = LocalDate.now(clock.withZone(BOGOTA_ZONE));
        return Period.between(birthDate, today).getYears();
    }
}
