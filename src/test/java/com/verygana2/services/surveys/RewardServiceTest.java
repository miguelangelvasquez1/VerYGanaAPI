package com.verygana2.services.surveys;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
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

        // Stubs comunes a todo grantReward exitoso.
        when(keyWalletService.getByConsumerId(CONSUMER_ID)).thenReturn(wallet);
        when(keyWalletService.calculatePurchaseExpiry()).thenReturn(java.time.ZonedDateTime.now());
        when(keyWalletService.calculateConnectivityExpiry()).thenReturn(java.time.ZonedDateTime.now());
        when(rewardRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
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
}
