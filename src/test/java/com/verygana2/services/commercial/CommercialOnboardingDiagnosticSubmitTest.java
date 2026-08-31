package com.verygana2.services.commercial;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import com.verygana2.dtos.user.commercial.onboarding.CommercialDiagnosticRequestDTO;
import com.verygana2.dtos.user.commercial.onboarding.RouteClassificationResponseDTO;
import com.verygana2.exceptions.commercial.OnboardingStepException;
import com.verygana2.mappers.CommercialOnboardingMapper;
import com.verygana2.mappers.CommercialOnboardingMapperImpl;
import com.verygana2.models.commercial.CommercialOnboarding;
import com.verygana2.models.enums.commercial.CommercialRoute;
import com.verygana2.models.enums.commercial.OnboardingStep;
import com.verygana2.models.enums.commercial.TechIntegrationNeed;
import com.verygana2.models.enums.commercial.diagnostic.DesiredActiveOffers;
import com.verygana2.models.enums.commercial.diagnostic.DirectSaleMode;
import com.verygana2.models.enums.commercial.diagnostic.FeeViability;
import com.verygana2.models.enums.commercial.diagnostic.IndependentHelp;
import com.verygana2.models.enums.commercial.diagnostic.InvestmentCapacity;
import com.verygana2.models.enums.commercial.diagnostic.MainActivity;
import com.verygana2.models.enums.commercial.diagnostic.MarketReachStructure;
import com.verygana2.models.enums.commercial.diagnostic.MetricsNeeded;
import com.verygana2.models.enums.commercial.diagnostic.Understanding;
import com.verygana2.models.enums.commercial.diagnostic.YesPartialNo;
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

/**
 * {@code submitDiagnostic}: el POST del diagnóstico acepta o el cuestionario de
 * caracterización (→ modalidad A/B/C) o la ruta alternativa de integración técnica
 * ({@code techIntegrationNeeds} → Ruta D, contacto con asesor).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CommercialOnboardingServiceImpl.submitDiagnostic")
class CommercialOnboardingDiagnosticSubmitTest {

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

    private final CommercialOnboardingMapper mapper = new CommercialOnboardingMapperImpl();
    private final CommercialDiagnosticClassifier classifier = new CommercialDiagnosticClassifier();

    private CommercialOnboardingServiceImpl service;
    private CommercialOnboarding onboarding;

    private static final long USER_ID = 42L;

    @BeforeEach
    void setUp() {
        service = new CommercialOnboardingServiceImpl(
                onboardingRepository, commercialDetailsRepository, commercialContractRepository,
                planRepository, screeningService, legalDocumentRepository, locationService,
                eventPublisher, documentService, mapper, planService, classifier,
                diagnosticQuestionnaireRepository);

        onboarding = new CommercialOnboarding();
        onboarding.setLegalIdentificationCompletedAt(ZonedDateTime.now());
        onboarding.setCurrentStep(OnboardingStep.DIAGNOSTIC_PENDING);

        lenient().when(onboardingRepository.findByCommercialDetails_Id(USER_ID))
                .thenReturn(Optional.of(onboarding));
        lenient().when(onboardingRepository.save(any(CommercialOnboarding.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @Nested
    @DisplayName("ruta alternativa: integración técnica")
    class TechIntegration {

        @Test
        @DisplayName("con techIntegrationNeeds → Ruta D, ADVISOR_CONTACT_PENDING y negociación especial")
        void routesToAdvisorContact() {
            CommercialDiagnosticRequestDTO dto = new CommercialDiagnosticRequestDTO();
            dto.setTechIntegrationNeeds(Set.of(
                    TechIntegrationNeed.API, TechIntegrationNeed.CONCILIACION,
                    TechIntegrationNeed.ACTIVACION_AUTOMATICA));
            dto.setIntegrationDetails("Necesitamos conciliación diaria por API y activación automática de códigos.");

            RouteClassificationResponseDTO result = service.submitDiagnostic(USER_ID, dto);

            assertThat(result.getRoute()).isEqualTo(CommercialRoute.D);
            assertThat(result.getModalityLabel()).isEqualTo("Integración técnica");
            assertThat(result.isConfirmed()).isFalse();

            assertThat(onboarding.getCurrentStep()).isEqualTo(OnboardingStep.ADVISOR_CONTACT_PENDING);
            assertThat(onboarding.getRoute()).isEqualTo(CommercialRoute.D);
            assertThat(onboarding.isRouteConfirmed()).isTrue();
            assertThat(onboarding.getRequiresSpecialNegotiation()).isTrue();
            assertThat(onboarding.getIntegrationDetails()).contains("conciliación diaria");
            assertThat(onboarding.getTechIntegrationNeeds())
                    .containsExactlyInAnyOrder(TechIntegrationNeed.API, TechIntegrationNeed.CONCILIACION,
                            TechIntegrationNeed.ACTIVACION_AUTOMATICA);
            assertThat(onboarding.getSelectedPlan()).isNull();
        }

        @Test
        @DisplayName("techIntegrationNeeds sin integrationDetails → rechaza")
        void requiresIntegrationDetails() {
            CommercialDiagnosticRequestDTO dto = new CommercialDiagnosticRequestDTO();
            dto.setTechIntegrationNeeds(Set.of(TechIntegrationNeed.API));

            assertThatThrownBy(() -> service.submitDiagnostic(USER_ID, dto))
                    .isInstanceOf(OnboardingStepException.class)
                    .hasMessageContaining("integración técnica");
        }
    }

    @Nested
    @DisplayName("cuestionario normal")
    class Questionnaire {

        @Test
        @DisplayName("sin techIntegrationNeeds → clasifica modalidad y va a CLASSIFICATION_PENDING")
        void classifiesModalityAndAdvances() {
            RouteClassificationResponseDTO result = service.submitDiagnostic(USER_ID, minimalTypeAAnswers());

            assertThat(result.getRoute()).isIn(CommercialRoute.A, CommercialRoute.B, CommercialRoute.C);
            assertThat(onboarding.getCurrentStep()).isEqualTo(OnboardingStep.CLASSIFICATION_PENDING);
            assertThat(onboarding.isRouteConfirmed()).isFalse();
            assertThat(onboarding.getRequiresSpecialNegotiation()).isFalse();
            assertThat(onboarding.getTechIntegrationNeeds()).isEmpty();
        }

        @Test
        @DisplayName("falta una pregunta obligatoria → rechaza")
        void requiresMandatoryAnswers() {
            CommercialDiagnosticRequestDTO dto = minimalTypeAAnswers();
            dto.setMainActivity(null);

            assertThatThrownBy(() -> service.submitDiagnostic(USER_ID, dto))
                    .isInstanceOf(OnboardingStepException.class)
                    .hasMessageContaining("actividad principal");
        }
    }

    private static CommercialDiagnosticRequestDTO minimalTypeAAnswers() {
        CommercialDiagnosticRequestDTO dto = new CommercialDiagnosticRequestDTO();
        dto.setMainActivity(MainActivity.RESTAURANTE);
        dto.setMarketReachStructure(MarketReachStructure.PROPIETARIO);
        dto.setSellsDirectlyAndConcentrated(YesPartialNo.SI);
        dto.setDirectSaleToConsumer(DirectSaleMode.SI);
        dto.setDesiredActiveOffers(DesiredActiveOffers.HASTA_TRES);
        dto.setMetricsNeeded(MetricsNeeded.BASICAS);
        dto.setIndependentEntrepreneursHelp(IndependentHelp.NO);
        dto.setTypeAMonthlyFeeViable(FeeViability.SI);
        dto.setTypeBInvestmentCapacity(InvestmentCapacity.NO);
        dto.setAcceptsPremiumBrandFocus(Understanding.NO);
        return dto;
    }
}
