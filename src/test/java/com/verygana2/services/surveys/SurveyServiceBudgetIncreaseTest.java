package com.verygana2.services.surveys;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.access.AccessDeniedException;

import com.verygana2.dtos.BudgetIncreaseResponseDTO;
import com.verygana2.dtos.survey.IncreaseSurveyBudgetRequest;
import com.verygana2.exceptions.InsufficientFundsException;
import com.verygana2.exceptions.StaleBudgetException;
import com.verygana2.exceptions.surveys.SurveyNotFoundException;
import com.verygana2.exceptions.surveys.SurveySuspendedException;
import com.verygana2.models.PricingConfig;
import com.verygana2.models.finance.Wallet;
import com.verygana2.models.finance.plans.RequirePlanCapability.Capability;
import com.verygana2.models.surveys.Survey;
import com.verygana2.models.surveys.Survey.SurveyStatus;
import com.verygana2.models.surveys.SurveyQuestion;
import com.verygana2.models.userDetails.CommercialDetails;
import com.verygana2.repositories.WalletRepository;
import com.verygana2.repositories.surveys.SurveyRepository;
import com.verygana2.services.PricingConfigService;
import com.verygana2.services.plans.PlanFeatureGuard;
import com.verygana2.services.plans.PlanFeatureGuard.PlanCapabilityException;

import jakarta.validation.ValidationException;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("SurveyService — aumento de presupuesto")
class SurveyServiceBudgetIncreaseTest {

    private static final Long SURVEY_ID = 9L;
    private static final Long COMMERCIAL_ID = 7L;
    private static final long PRICE_PER_QUESTION = 50L;
    private static final int QUESTIONS = 2;
    /** 2 preguntas × 50 ¢ */
    private static final long COST_PER_RESPONSE = QUESTIONS * PRICE_PER_QUESTION;

    @Mock SurveyRepository surveyRepository;
    @Mock WalletRepository walletRepository;
    @Mock PlanFeatureGuard planFeatureGuard;
    @Mock PricingConfigService pricingConfigService;

    @InjectMocks SurveyService surveyService;

    private Wallet wallet;

    @BeforeEach
    void setUp() {
        // Minimo vigente == precio de la encuesta de prueba (50 c): justo en el limite.
        when(pricingConfigService.getCurrentValue(PricingConfig.PricingType.SURVEY_REWARD_PER_QUESTION_CENTS))
                .thenReturn(PRICE_PER_QUESTION);

        wallet = new Wallet();
        wallet.setBalanceCents(1_000_000L);
        when(walletRepository.findByCommercialIdForUpdate(COMMERCIAL_ID)).thenReturn(Optional.of(wallet));
        when(walletRepository.save(any(Wallet.class))).thenAnswer(inv -> inv.getArgument(0));
        when(surveyRepository.save(any(Survey.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    /** Encuesta de 2 preguntas a 50 ¢, con cupo de 10 respuestas y {@code responseCount} ya recibidas. */
    private Survey surveyWith(SurveyStatus status, int responseCount) {
        CommercialDetails creator = new CommercialDetails();
        creator.setId(COMMERCIAL_ID);

        List<SurveyQuestion> questions = new ArrayList<>();
        for (int i = 0; i < QUESTIONS; i++) {
            questions.add(SurveyQuestion.builder().id((long) i + 1).orderIndex(i).build());
        }

        return Survey.builder()
                .id(SURVEY_ID)
                .creator(creator)
                .status(status)
                .rewardAmountPerQuestionCents(PRICE_PER_QUESTION)
                .maxResponses(10)
                .responseCount(responseCount)
                .questions(questions)
                .build();
    }

    private void givenSurvey(Survey survey) {
        when(surveyRepository.findByIdForUpdate(SURVEY_ID)).thenReturn(Optional.of(survey));
    }

    /** Aumento con el cupo que el cliente ve hoy (10 respuestas, el default de {@link #surveyWith}). */
    private BudgetIncreaseResponseDTO increase(int additionalResponses) {
        return increase(10, additionalResponses);
    }

    private BudgetIncreaseResponseDTO increase(int expectedMaxResponses, int additionalResponses) {
        return surveyService.increaseSurveyBudget(SURVEY_ID,
                IncreaseSurveyBudgetRequest.builder()
                        .expectedMaxResponses(expectedMaxResponses)
                        .additionalResponses(additionalResponses)
                        .build(),
                COMMERCIAL_ID);
    }

    @Nested
    @DisplayName("estados que admiten aumento")
    class AllowedStatuses {

        @Test
        @DisplayName("ACTIVE: cobra preguntas × precio × respuestas, sube maxResponses y conserva el estado")
        void active_chargesAndKeepsStatus() {
            Survey survey = surveyWith(SurveyStatus.ACTIVE, 4);
            givenSurvey(survey);

            BudgetIncreaseResponseDTO result = increase(5);

            assertThat(survey.getMaxResponses()).isEqualTo(15);
            assertThat(survey.getStatus()).isEqualTo(SurveyStatus.ACTIVE);
            assertThat(wallet.getBalanceCents()).isEqualTo(1_000_000L - 5 * COST_PER_RESPONSE);
            verify(surveyRepository).save(survey);
            verify(planFeatureGuard, never()).assertCanReopen(any(), any());

            assertThat(result.getChargedCents()).isEqualTo(5 * COST_PER_RESPONSE);
            assertThat(result.getTotalBudgetCents()).isEqualTo(15 * COST_PER_RESPONSE);
            assertThat(result.getRemainingBudgetCents()).isEqualTo(11 * COST_PER_RESPONSE);  // (15 − 4)
            assertThat(result.getStatus()).isEqualTo("ACTIVE");
            assertThat(result.isReopened()).isFalse();
        }

        @Test
        @DisplayName("PAUSED: sube el presupuesto pero sigue PAUSED")
        void paused_staysPaused() {
            Survey survey = surveyWith(SurveyStatus.PAUSED, 2);
            givenSurvey(survey);

            BudgetIncreaseResponseDTO result = increase(3);

            assertThat(survey.getMaxResponses()).isEqualTo(13);
            assertThat(survey.getStatus()).isEqualTo(SurveyStatus.PAUSED);
            assertThat(result.isReopened()).isFalse();
        }

        @Test
        @DisplayName("COMPLETED: se reabre a ACTIVE y exige cupo MAX_SURVEYS")
        void completed_reopens() {
            Survey survey = surveyWith(SurveyStatus.COMPLETED, 10);
            givenSurvey(survey);

            BudgetIncreaseResponseDTO result = increase(5);

            verify(planFeatureGuard).assertCanReopen(COMMERCIAL_ID, Capability.MAX_SURVEYS);
            assertThat(survey.getStatus()).isEqualTo(SurveyStatus.ACTIVE);
            assertThat(survey.getMaxResponses()).isEqualTo(15);

            assertThat(result.isReopened()).isTrue();
            assertThat(result.getStatus()).isEqualTo("ACTIVE");
            assertThat(result.getRemainingBudgetCents()).isEqualTo(5 * COST_PER_RESPONSE);
        }
    }

    @Nested
    @DisplayName("rechazos")
    class Rejections {

        @ParameterizedTest
        @EnumSource(value = SurveyStatus.class, names = {"DRAFT", "PENDING_REVIEW", "APPROVED", "REJECTED"})
        @DisplayName("estados fuera del ciclo activo: ValidationException y sin tocar la wallet")
        void disallowedStatus_rejected(SurveyStatus status) {
            Survey survey = surveyWith(status, 0);
            givenSurvey(survey);

            assertThatThrownBy(() -> increase(5)).isInstanceOf(ValidationException.class);

            assertThat(survey.getMaxResponses()).isEqualTo(10);
            assertThat(wallet.getBalanceCents()).isEqualTo(1_000_000L);
            verify(walletRepository, never()).save(any());
            verify(surveyRepository, never()).save(any());
        }

        @Test
        @DisplayName("SUSPENDED (congelada por un admin): SurveySuspendedException y sin cobrar")
        void suspended_rejected() {
            givenSurvey(surveyWith(SurveyStatus.SUSPENDED, 0));

            assertThatThrownBy(() -> increase(5)).isInstanceOf(SurveySuspendedException.class);
            verify(walletRepository, never()).save(any());
        }

        @Test
        @DisplayName("encuesta de otro comercial: AccessDeniedException y sin cobrar")
        void notOwned_accessDenied() {
            Survey survey = surveyWith(SurveyStatus.ACTIVE, 0);
            survey.getCreator().setId(999L);
            givenSurvey(survey);

            assertThatThrownBy(() -> increase(5)).isInstanceOf(AccessDeniedException.class);
            verify(walletRepository, never()).save(any());
        }

        @Test
        @DisplayName("encuesta inexistente: SurveyNotFoundException")
        void notFound() {
            when(surveyRepository.findByIdForUpdate(SURVEY_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> increase(5)).isInstanceOf(SurveyNotFoundException.class);
        }

        @Test
        @DisplayName("ventana cerrada (endsAt vencido): ValidationException y sin cobrar")
        void endsAtPassed_rejected() {
            Survey survey = surveyWith(SurveyStatus.ACTIVE, 0);
            survey.setEndsAt(ZonedDateTime.now().minusDays(1));
            givenSurvey(survey);

            assertThatThrownBy(() -> increase(5)).isInstanceOf(ValidationException.class);
            verify(walletRepository, never()).save(any());
        }

        @Test
        @DisplayName("saldo insuficiente: InsufficientFundsException y la encuesta queda intacta")
        void insufficientFunds_surveyUntouched() {
            wallet.setBalanceCents(5 * COST_PER_RESPONSE - 1);
            Survey survey = surveyWith(SurveyStatus.COMPLETED, 10);
            givenSurvey(survey);

            assertThatThrownBy(() -> increase(5)).isInstanceOf(InsufficientFundsException.class);

            assertThat(survey.getMaxResponses()).isEqualTo(10);
            assertThat(survey.getStatus()).isEqualTo(SurveyStatus.COMPLETED);
            verify(surveyRepository, never()).save(any());
        }

        @Test
        @DisplayName("COMPLETED sin cupo en el plan: PlanCapabilityException y sin cobrar")
        void completedWithoutSlot_rejectedBeforeCharging() {
            Survey survey = surveyWith(SurveyStatus.COMPLETED, 10);
            givenSurvey(survey);
            doThrow(new PlanCapabilityException("Límite de encuestas alcanzado"))
                    .when(planFeatureGuard).assertCanReopen(COMMERCIAL_ID, Capability.MAX_SURVEYS);

            assertThatThrownBy(() -> increase(5)).isInstanceOf(PlanCapabilityException.class);

            assertThat(survey.getStatus()).isEqualTo(SurveyStatus.COMPLETED);
            assertThat(wallet.getBalanceCents()).isEqualTo(1_000_000L);
            verify(walletRepository, never()).save(any());
            verify(surveyRepository, never()).save(any());
        }

        @Test
        @DisplayName("cupo esperado distinto del actual: StaleBudgetException (409) y sin cobrar")
        void staleExpectedMaxResponses_rejectedWithoutCharging() {
            Survey survey = surveyWith(SurveyStatus.ACTIVE, 0);
            givenSurvey(survey);

            assertThatThrownBy(() -> increase(7, 5)).isInstanceOf(StaleBudgetException.class);

            assertThat(survey.getMaxResponses()).isEqualTo(10);
            assertThat(wallet.getBalanceCents()).isEqualTo(1_000_000L);
            verify(walletRepository, never()).save(any());
            verify(surveyRepository, never()).save(any());
        }

        @Test
        @DisplayName("doble envio identico (doble clic / reintento): cobra una sola vez, el segundo es 409")
        void doubleSubmit_chargedOnce() {
            Survey survey = surveyWith(SurveyStatus.ACTIVE, 0);
            givenSurvey(survey);

            increase(10, 5);                                            // primer clic: aplica
            assertThatThrownBy(() -> increase(10, 5))                   // segundo clic: mismo "esperado"
                    .isInstanceOf(StaleBudgetException.class);

            assertThat(survey.getMaxResponses()).isEqualTo(15);
            assertThat(wallet.getBalanceCents()).isEqualTo(1_000_000L - 5 * COST_PER_RESPONSE);  // una sola vez
        }

        @Test
        @DisplayName("precio por pregunta por debajo del minimo vigente: ValidationException y sin cobrar")
        void belowCurrentMinimumPrice_rejected() {
            when(pricingConfigService.getCurrentValue(PricingConfig.PricingType.SURVEY_REWARD_PER_QUESTION_CENTS))
                    .thenReturn(60L);
            Survey survey = surveyWith(SurveyStatus.COMPLETED, 10);
            givenSurvey(survey);

            assertThatThrownBy(() -> increase(5))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("50")
                    .hasMessageContaining("60");

            assertThat(survey.getStatus()).isEqualTo(SurveyStatus.COMPLETED);
            verify(planFeatureGuard, never()).assertCanReopen(any(), any());
            verify(walletRepository, never()).save(any());
            verify(surveyRepository, never()).save(any());
        }
    }
}
