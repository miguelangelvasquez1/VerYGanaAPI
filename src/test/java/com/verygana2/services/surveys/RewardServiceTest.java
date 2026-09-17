package com.verygana2.services.surveys;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import com.verygana2.models.finance.KeyWallet;
import com.verygana2.models.surveys.Survey;
import com.verygana2.models.surveys.SurveyQuestion;
import com.verygana2.dtos.survey.submission.UserRewardsSummary;
import com.verygana2.exceptions.BusinessException;
import com.verygana2.models.surveys.SurveyReward;
import com.verygana2.models.surveys.SurveySession;
import com.verygana2.models.userDetails.ConsumerDetails;
import com.verygana2.repositories.finance.KeyTransactionRepository;
import com.verygana2.repositories.finance.KeyWalletRepository;
import com.verygana2.repositories.surveys.SurveyRewardRepository;
import com.verygana2.repositories.surveys.SurveySessionRepository;
import com.verygana2.services.finance.KeyWalletServiceImpl.RewardSplit;
import com.verygana2.services.interfaces.finance.KeyWalletService;
import com.verygana2.services.interfaces.levels.LevelService;

/**
 * Verifica que el multiplicador de nivel se aplique correctamente a las LLAVES
 * acreditadas al completar una encuesta (RewardService.creditPoints).
 *
 * El multiplicador se calcula en LevelService (probado aparte); aquí se cubre el
 * cableado: base × multiplicador → redondeo → split 75/25 → crédito en la wallet.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RewardService — multiplicador de nivel sobre las llaves")
class RewardServiceTest {

    private static final Long CONSUMER_ID = 42L;

    @Mock SurveyRewardRepository rewardRepository;
    @Mock SurveySessionRepository sessionRepository;
    @Mock KeyWalletRepository keyWalletRepository;
    @Mock KeyWalletService keyWalletService;
    @Mock KeyTransactionRepository keyTransactionRepository;
    @Mock ApplicationEventPublisher eventPublisher;
    @Mock LevelService levelService;

    @InjectMocks RewardService service;

    private KeyWallet wallet;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "keyValueCents", 1000L);

        wallet = new KeyWallet();

        // Stubs comunes a grantReward. lenient() porque los tests de solo lectura
        // (getUserRewardsSummary) no pasan por el camino de crédito.
        lenient().when(keyWalletService.getByConsumerId(CONSUMER_ID)).thenReturn(wallet);
        lenient().when(keyWalletService.calculatePurchaseExpiry()).thenReturn(java.time.ZonedDateTime.now());
        lenient().when(keyWalletService.calculateConnectivityExpiry()).thenReturn(java.time.ZonedDateTime.now());
        lenient().when(rewardRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    /** SurveySession con N preguntas y recompensa por pregunta en centavos. */
    private SurveySession sessionWith(int questions, long rewardPerQuestionCents) {
        ConsumerDetails consumer = new ConsumerDetails();
        consumer.setId(CONSUMER_ID);

        List<SurveyQuestion> qs = Collections.nCopies(questions, null);
        Survey survey = Survey.builder()
                .id(1L)
                .rewardAmountPerQuestionCents(rewardPerQuestionCents)
                .questions(qs)
                .build();

        return SurveySession.builder()
                .id(7L)
                .survey(survey)
                .consumer(consumer)
                .build();
    }

    @Nested
    @DisplayName("aplica el multiplicador antes de acreditar")
    class AppliesMultiplier {

        @Test
        @DisplayName("ORO (×0.7): 4 preguntas × 500 = 2000 base → 1400 llaves")
        void oroMultiplier() {
            SurveySession session = sessionWith(4, 500);            // base = 2000
            when(levelService.getMultiplier(CONSUMER_ID)).thenReturn(0.7);
            when(keyWalletService.calculate(1400L)).thenReturn(new RewardSplit(1050, 350));

            SurveyReward reward = service.grantReward(session);

            // El split se calcula sobre el monto YA multiplicado, no sobre la base.
            verify(keyWalletService).calculate(1400L);
            assertThat(wallet.getAvailableKeysCents()).isEqualTo(1400L);
            assertThat(wallet.getPurchaseKeysCents()).isEqualTo(1050L);
            assertThat(wallet.getConnectivityKeysCents()).isEqualTo(350L);
            assertThat(reward.getStatus()).isEqualTo(SurveyReward.RewardStatus.PROCESSED);
        }

        @Test
        @DisplayName("DIAMANTE (×1.0): sin reducción, base = llaves")
        void diamanteKeepsFullValue() {
            SurveySession session = sessionWith(2, 800);            // base = 1600
            when(levelService.getMultiplier(CONSUMER_ID)).thenReturn(1.0);
            when(keyWalletService.calculate(1600L)).thenReturn(new RewardSplit(1200, 400));

            service.grantReward(session);

            verify(keyWalletService).calculate(1600L);
            assertThat(wallet.getAvailableKeysCents()).isEqualTo(1600L);
        }

        @Test
        @DisplayName("redondea en vez de truncar (5 × 0.7 = 3.5 → 4)")
        void roundsInsteadOfTruncating() {
            SurveySession session = sessionWith(1, 5);              // base = 5
            when(levelService.getMultiplier(CONSUMER_ID)).thenReturn(0.7);
            when(keyWalletService.calculate(4L)).thenReturn(new RewardSplit(3, 1));

            service.grantReward(session);

            verify(keyWalletService).calculate(4L);                 // 3.5 → 4, no 3
            assertThat(wallet.getAvailableKeysCents()).isEqualTo(4L);
        }

        @Test
        @DisplayName("con beneficios pausados gana como BRONCE (×0.5)")
        void pausedEarnsAsBronce() {
            SurveySession session = sessionWith(4, 500);            // base = 2000
            // LevelService reporta el multiplicador de BRONCE cuando está pausado.
            when(levelService.getMultiplier(CONSUMER_ID)).thenReturn(0.5);
            when(keyWalletService.calculate(1000L)).thenReturn(new RewardSplit(750, 250));

            service.grantReward(session);

            verify(keyWalletService).calculate(1000L);
            assertThat(wallet.getAvailableKeysCents()).isEqualTo(1000L);
        }
    }

    @Nested
    @DisplayName("atomicidad: un fallo de crédito no deja la encuesta consumida")
    class FailureHandling {

        @Test
        @DisplayName("fallo al acreditar: lanza BusinessException (422 accionable) y no publica el XP")
        void creditFailureThrowsBusinessException() {
            SurveySession session = sessionWith(4, 500);
            when(levelService.getMultiplier(CONSUMER_ID)).thenReturn(0.7);
            when(keyWalletService.calculate(1400L)).thenReturn(new RewardSplit(1050, 350));
            when(keyWalletRepository.save(any()))
                    .thenThrow(new IllegalStateException("fallo al persistir la billetera"));

            // Antes esto se tragaba la excepción y commiteaba: encuesta consumida,
            // reward FAILED y llaves acreditadas igual.
            assertThatThrownBy(() -> service.grantReward(session))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("vuelve a enviar la encuesta");

            verify(eventPublisher, org.mockito.Mockito.never()).publishEvent(any(Object.class));
        }

        @Test
        @DisplayName("éxito: la recompensa queda sin liquidar, para que el job por lotes la recoja")
        void successLeavesRowUnsettled() {
            SurveySession session = sessionWith(4, 500);
            when(levelService.getMultiplier(CONSUMER_ID)).thenReturn(0.7);
            when(keyWalletService.calculate(1400L)).thenReturn(new RewardSplit(1050, 350));

            SurveyReward reward = service.grantReward(session);

            assertThat(reward.isIssuanceSettled()).isFalse();
        }
    }

    @Nested
    @DisplayName("el usuario ve lo acreditado, no lo que financió el anunciante")
    class ReportsCreditedAmount {

        @Test
        @DisplayName("ORO (×0.7): la recompensa guarda amountCents=2000 y creditedAmountCents=1400")
        void storesBothAmounts() {
            SurveySession session = sessionWith(4, 500);            // base = 2000
            when(levelService.getMultiplier(CONSUMER_ID)).thenReturn(0.7);
            when(keyWalletService.calculate(1400L)).thenReturn(new RewardSplit(1050, 350));

            SurveyReward reward = service.grantReward(session);

            assertThat(reward.getAmountCents()).isEqualTo(2000L);
            assertThat(reward.getCreditedAmountCents()).isEqualTo(1400L);
        }

        @Test
        @DisplayName("el resumen lista lo acreditado (1400 → 1 llave), no la base (2000 → 2 llaves)")
        void summaryListsCreditedNotFunded() {
            SurveyReward processed = SurveyReward.builder()
                    .id(1L)
                    .amountCents(2000L)              // financiado
                    .creditedAmountCents(1400L)      // acreditado
                    .status(SurveyReward.RewardStatus.PROCESSED)
                    .build();

            when(sessionRepository.countCompletedByConsumer(CONSUMER_ID)).thenReturn(1L);
            when(rewardRepository.getTotalRewardsByConsumer(CONSUMER_ID))
                    .thenReturn(java.math.BigDecimal.valueOf(1400L));
            when(rewardRepository.findBySessionConsumerId(eq(CONSUMER_ID), any()))
                    .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(processed)));

            UserRewardsSummary summary = service.getUserRewardsSummary(CONSUMER_ID);

            assertThat(summary.getTotalKeysEarned()).isEqualTo(1L);
            assertThat(summary.getRecentRewards().get(0).getAmountKeys()).isEqualTo(1L);
        }

        @Test
        @DisplayName("filas previas a la columna (credited nulo): cae a la base en vez de reportar cero")
        void legacyRowsFallBackToFundedAmount() {
            SurveyReward legacy = SurveyReward.builder()
                    .id(2L)
                    .amountCents(2000L)
                    .creditedAmountCents(null)       // fila anterior a credited_amount
                    .status(SurveyReward.RewardStatus.PROCESSED)
                    .build();

            when(sessionRepository.countCompletedByConsumer(CONSUMER_ID)).thenReturn(1L);
            when(rewardRepository.getTotalRewardsByConsumer(CONSUMER_ID))
                    .thenReturn(java.math.BigDecimal.valueOf(2000L));
            when(rewardRepository.findBySessionConsumerId(eq(CONSUMER_ID), any()))
                    .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(legacy)));

            UserRewardsSummary summary = service.getUserRewardsSummary(CONSUMER_ID);

            assertThat(summary.getRecentRewards().get(0).getAmountKeys()).isEqualTo(2L);
        }
    }
}
