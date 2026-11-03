package com.verygana2.services.commercial;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import com.verygana2.config.TreasuryConfig;
import com.verygana2.dtos.user.commercial.onboarding.ContractApproveRequestDTO;
import com.verygana2.exceptions.commercial.OnboardingStepException;
import com.verygana2.mappers.CommercialOnboardingMapper;
import com.verygana2.models.commercial.CommercialContract;
import com.verygana2.models.commercial.CommercialOnboarding;
import com.verygana2.models.enums.commercial.ContractPurpose;
import com.verygana2.models.enums.commercial.ContractStatus;
import com.verygana2.models.enums.commercial.OnboardingStep;
import com.verygana2.models.enums.declarations.ManifestationDecision;
import com.verygana2.models.userDetails.CommercialDetails;
import com.verygana2.repositories.commercial.CommercialContractRepository;
import com.verygana2.repositories.commercial.CommercialDocumentRepository;
import com.verygana2.repositories.commercial.CommercialOnboardingRepository;
import com.verygana2.repositories.details.CommercialDetailsRepository;
import com.verygana2.services.interfaces.EmailService;
import com.verygana2.services.interfaces.commercial.ESignatureService;
import com.verygana2.services.interfaces.declarations.DeclarationService;
import com.verygana2.storage.service.R2Service;

/**
 * La casilla del compromiso de formalización (apartado 9 de la Hoja Final) se registra al
 * aprobar, pero nunca bloquea la aprobación: la ausencia o la negativa dan lugar a aclaración.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CommercialContractServiceImpl.businessApprove — casilla del apartado 9")
class CommercialContractBusinessApproveTest {

    private static final long USER_ID = 7L;

    @Mock private CommercialOnboardingRepository onboardingRepository;
    @Mock private CommercialContractRepository contractRepository;
    @Mock private CommercialDocumentRepository documentRepository;
    @Mock private CommercialDetailsRepository commercialDetailsRepository;
    @Mock private CommercialOnboardingMapper commercialOnboardingMapper;
    @Mock private ContractTemplateLoader templateLoader;
    @Mock private ContractPdfRenderer pdfRenderer;
    @Mock private R2Service r2Service;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private ESignatureService esignatureService;
    @Mock private EmailService emailService;
    @Mock private TreasuryConfig treasuryConfig;
    @Mock private DeclarationService declarationService;

    @InjectMocks private CommercialContractServiceImpl service;

    private CommercialOnboarding onboarding;
    private CommercialContract contract;

    @BeforeEach
    void setUp() {
        CommercialDetails details = new CommercialDetails();
        details.setId(USER_ID);
        onboarding = new CommercialOnboarding();
        onboarding.setId(3L);
        onboarding.setCommercialDetails(details);
        onboarding.setCurrentStep(OnboardingStep.BUSINESS_REVIEW_PENDING);
        contract = new CommercialContract();
        contract.setId(30L);
        contract.setVersion(1);
        contract.setCommercial(details);
        contract.setOnboarding(onboarding);
        contract.setPurpose(ContractPurpose.ONBOARDING);
        contract.setStatus(ContractStatus.PENDING_BUSINESS_REVIEW);

        when(onboardingRepository.findByCommercialDetails_Id(USER_ID)).thenReturn(Optional.of(onboarding));
        when(contractRepository.findByOnboarding_Id(3L)).thenReturn(Optional.of(contract));
    }

    @Test
    @DisplayName("sin casilla marcada (sin cuerpo) la aprobación sigue y pasa a revisión de VERYGANA")
    void approvalContinuesWithoutManifestation() {
        service.businessApprove(USER_ID, null, null, "203.0.113.7", "JUnit");

        assertThat(contract.getStatus()).isEqualTo(ContractStatus.PENDING_VERYGANA_REVIEW);
        assertThat(onboarding.getCurrentStep()).isEqualTo(OnboardingStep.VERYGANA_REVIEW_PENDING);
        verify(declarationService).recordContractManifestation(eq(onboarding), eq(contract), isNull(), isNull(),
                eq(USER_ID), eq("203.0.113.7"), eq("JUnit"));
    }

    @Test
    @DisplayName("una negativa expresa tampoco rechaza el contrato automáticamente")
    void refusalDoesNotRejectContract() {
        ContractApproveRequestDTO refusal = new ContractApproveRequestDTO();
        refusal.setDecision(ManifestationDecision.REFUSE);
        refusal.setDeclarationVersion("1");

        service.businessApprove(USER_ID, refusal, "k-1", null, null);

        assertThat(contract.getStatus()).isEqualTo(ContractStatus.PENDING_VERYGANA_REVIEW);
        verify(declarationService).recordContractManifestation(eq(onboarding), eq(contract), eq(refusal), eq("k-1"),
                eq(USER_ID), any(), any());
    }

    @Test
    @DisplayName("un contrato que no está en revisión del empresario no registra manifestación")
    void wrongStatusRecordsNothing() {
        contract.setStatus(ContractStatus.PENDING_VERYGANA_REVIEW);

        assertThatThrownBy(() -> service.businessApprove(USER_ID, null, null, null, null))
                .isInstanceOf(OnboardingStepException.class);
        verify(declarationService, never()).recordContractManifestation(any(), any(), any(), any(), any(), any(), any());
    }
}
