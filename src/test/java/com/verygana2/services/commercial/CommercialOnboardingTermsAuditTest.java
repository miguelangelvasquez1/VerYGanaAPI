package com.verygana2.services.commercial;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import com.verygana2.config.TreasuryConfig;
import com.verygana2.dtos.user.commercial.onboarding.TermsAcceptanceRequestDTO;
import com.verygana2.mappers.CommercialOnboardingMapperImpl;
import com.verygana2.models.commercial.CommercialOnboarding;
import com.verygana2.models.enums.commercial.OnboardingStep;
import com.verygana2.models.enums.legal.LegalDocumentType;
import com.verygana2.models.legal.LegalDocument;
import com.verygana2.models.userDetails.CommercialDetails;
import com.verygana2.repositories.commercial.CommercialContractRepository;
import com.verygana2.repositories.commercial.CommercialOnboardingRepository;
import com.verygana2.repositories.commercial.DiagnosticQuestionnaireRepository;
import com.verygana2.repositories.details.CommercialDetailsRepository;
import com.verygana2.repositories.finance.plans.PlanRepository;
import com.verygana2.repositories.legal.LegalDocumentRepository;
import com.verygana2.services.LocationService;
import com.verygana2.services.interfaces.commercial.CommercialDocumentService;
import com.verygana2.services.interfaces.compliance.ScreeningService;
import com.verygana2.services.interfaces.finance.PlanService;
import com.verygana2.utils.audit.AuditEvent;
import com.verygana2.utils.audit.AuditLevel;

/**
 * La aceptación de T&C es evidencia legal: su auditoría va en CRITICAL, que
 * AuditCleanupJob no borra (INFO se elimina a los 90 días).
 */
@ExtendWith(MockitoExtension.class)
class CommercialOnboardingTermsAuditTest {

    @Mock private CommercialOnboardingRepository onboardingRepository;
    @Mock private CommercialDetailsRepository commercialDetailsRepository;
    @Mock private CommercialContractRepository commercialContractRepository;
    @Mock private PlanRepository planRepository;
    @Mock private ScreeningService screeningService;
    @Mock private LegalDocumentRepository legalDocumentRepository;
    @Mock private LocationService locationService;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private CommercialDocumentService documentService;
    @Mock private PlanService planService;
    @Mock private DiagnosticQuestionnaireRepository diagnosticQuestionnaireRepository;
    @Mock private TreasuryConfig treasuryConfig;

    private static final long USER_ID = 42L;

    @Test
    void termsAcceptedAuditIsCritical() {
        CommercialOnboardingServiceImpl service = new CommercialOnboardingServiceImpl(
                onboardingRepository, commercialDetailsRepository, commercialContractRepository,
                planRepository, screeningService, legalDocumentRepository, locationService,
                eventPublisher, documentService, new CommercialOnboardingMapperImpl(), planService,
                new CommercialDiagnosticClassifier(), diagnosticQuestionnaireRepository, treasuryConfig);

        CommercialOnboarding onboarding = new CommercialOnboarding();
        onboarding.setCommercialDetails(new CommercialDetails());
        onboarding.setCurrentStep(OnboardingStep.TERMS_PENDING);
        when(onboardingRepository.findByCommercialDetails_Id(USER_ID)).thenReturn(Optional.of(onboarding));
        lenient().when(onboardingRepository.save(any(CommercialOnboarding.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        LegalDocument terms = new LegalDocument();
        terms.setType(LegalDocumentType.BUSINESS_OWNER_TERMS_AND_CONDITIONS);
        terms.setVersion("1.0");
        terms.setDocumentUrl("https://example.test/terms-1.0.pdf");
        when(legalDocumentRepository.findByTypeAndVersion(
                LegalDocumentType.BUSINESS_OWNER_TERMS_AND_CONDITIONS, "1.0")).thenReturn(Optional.of(terms));

        TermsAcceptanceRequestDTO dto = new TermsAcceptanceRequestDTO();
        dto.setTermsVersion("1.0");
        dto.setAccepted(true);

        service.acceptTerms(USER_ID, dto, "203.0.113.7", "JUnit");

        ArgumentCaptor<AuditEvent> audit = ArgumentCaptor.forClass(AuditEvent.class);
        verify(eventPublisher).publishEvent(audit.capture());
        assertThat(audit.getValue().getAction()).isEqualTo("TERMS_ACCEPTED");
        assertThat(audit.getValue().getLevel()).isEqualTo(AuditLevel.CRITICAL);
    }
}
