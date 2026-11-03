package com.verygana2.services.commercial;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import com.verygana2.models.commercial.CommercialDocument;
import com.verygana2.models.commercial.CommercialOnboarding;
import com.verygana2.models.enums.commercial.CommercialDocumentStatus;
import com.verygana2.models.enums.commercial.CommercialDocumentType;
import com.verygana2.models.enums.commercial.OnboardingStep;
import com.verygana2.models.enums.commercial.PersonType;
import com.verygana2.models.userDetails.CommercialDetails;
import com.verygana2.repositories.commercial.CommercialDocumentRepository;
import com.verygana2.repositories.commercial.CommercialOnboardingRepository;
import com.verygana2.repositories.tax.TaxProfileRepository;
import com.verygana2.storage.service.R2Service;

/**
 * La ficha tributaria va entre documentos y contrato. Si el empresario ya la envió (p. ej.
 * vuelve a documentos tras un rechazo), no se le pide otra vez.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CommercialDocumentServiceImpl — paso de ficha tributaria")
class CommercialDocumentTaxProfileStepTest {

    private static final long USER_ID = 7L;

    @Mock private CommercialOnboardingRepository onboardingRepository;
    @Mock private CommercialDocumentRepository documentRepository;
    @Mock private R2Service r2Service;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private TaxProfileRepository taxProfileRepository;

    @InjectMocks private CommercialDocumentServiceImpl service;

    private CommercialOnboarding onboarding;

    @BeforeEach
    void setUp() {
        CommercialDetails details = new CommercialDetails();
        details.setId(USER_ID);
        onboarding = new CommercialOnboarding();
        onboarding.setId(3L);
        onboarding.setCommercialDetails(details);
        onboarding.setPersonType(PersonType.NATURAL);
        onboarding.setPlanAcceptedAt(ZonedDateTime.now());
        onboarding.setCurrentStep(OnboardingStep.DOCUMENTS_PENDING);
        when(onboardingRepository.findByCommercialDetails_Id(USER_ID)).thenReturn(Optional.of(onboarding));

        List<CommercialDocument> required = new ArrayList<>();
        for (CommercialDocumentType type : List.of(CommercialDocumentType.RUT,
                CommercialDocumentType.CEDULA_REPRESENTANTE, CommercialDocumentType.CERTIFICACION_BANCARIA)) {
            CommercialDocument d = new CommercialDocument();
            d.setDocumentType(type);
            d.setStatus(CommercialDocumentStatus.VALIDATED);
            required.add(d);
        }
        lenient().when(documentRepository.findByOnboarding_IdAndStatus(eq(3L), eq(CommercialDocumentStatus.VALIDATED)))
                .thenReturn(required);
        lenient().when(documentRepository.findByOnboarding_IdAndStatusNot(anyLong(), eq(CommercialDocumentStatus.ORPHANED)))
                .thenReturn(required);
    }

    @Test
    @DisplayName("sin ficha enviada, al terminar documentos pasa a TAX_PROFILE_PENDING")
    void goesToTaxProfile() {
        when(taxProfileRepository.existsByCommercial_Id(USER_ID)).thenReturn(false);

        service.continueToContract(USER_ID);

        assertThat(onboarding.getCurrentStep()).isEqualTo(OnboardingStep.TAX_PROFILE_PENDING);
    }

    @Test
    @DisplayName("con ficha ya enviada, salta directo a CONTRACT_PENDING")
    void skipsWhenAlreadySubmitted() {
        when(taxProfileRepository.existsByCommercial_Id(USER_ID)).thenReturn(true);

        service.continueToContract(USER_ID);

        assertThat(onboarding.getCurrentStep()).isEqualTo(OnboardingStep.CONTRACT_PENDING);
    }
}
