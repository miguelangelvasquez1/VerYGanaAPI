package com.verygana2.config;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.verygana2.models.ads.Ad;
import com.verygana2.models.ads.AdLike;
import com.verygana2.models.ads.AdLikeId;
import com.verygana2.models.ads.AdWatchSession;
import com.verygana2.models.surveys.SurveyReward;
import com.verygana2.models.surveys.SurveySession;
import com.verygana2.models.userDetails.ConsumerDetails;
import com.verygana2.repositories.AdLikeRepository;
import com.verygana2.repositories.AdWatchSessionRepository;
import com.verygana2.repositories.finance.KeyTransactionRepository;
import com.verygana2.repositories.surveys.SurveyRewardRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * El backfill reconstruye credited_amount desde KeyTransaction para las filas
 * anteriores a esa columna. Lo crítico es que sea idempotente y que NO mueva
 * dinero: marca las filas como liquidadas para que el job por lotes no liquide
 * retroactivamente un diferencial histórico.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("KeyIssuanceBackfillRunner")
class KeyIssuanceBackfillRunnerTest {

    @Mock AdLikeRepository adLikeRepository;
    @Mock SurveyRewardRepository surveyRewardRepository;
    @Mock AdWatchSessionRepository adWatchSessionRepository;
    @Mock KeyTransactionRepository keyTransactionRepository;

    private KeyIssuanceBackfillRunner runner() {
        return new KeyIssuanceBackfillRunner(
                adLikeRepository, surveyRewardRepository,
                adWatchSessionRepository, keyTransactionRepository);
    }

    private AdLike legacyLike(long funded) {
        AdLike like = new AdLike();
        like.setId(new AdLikeId(42L, 900L));
        like.setRewardAmount(funded);
        like.setCreditedAmountCents(null);
        return like;
    }

    @Test
    @DisplayName("rellena credited desde KeyTransaction y marca la fila liquidada (no mueve dinero)")
    void backfillsFromLedgerAndMarksSettled() {
        AdLike like = legacyLike(10_000L);
        UUID sessionId = UUID.randomUUID();
        AdWatchSession session = new AdWatchSession();
        session.setId(sessionId);

        when(adLikeRepository.findWithoutCreditedAmount()).thenReturn(List.of(like));
        when(adWatchSessionRepository.findLikedSessions(42L, 900L)).thenReturn(List.of(session));
        when(keyTransactionRepository.sumInteractionCreditByReference(sessionId)).thenReturn(7_000L);
        when(surveyRewardRepository.findWithoutCreditedAmount()).thenReturn(List.of());

        runner().run(null);

        assertThat(like.getCreditedAmountCents()).isEqualTo(7_000L);
        // Liquidada: el job por lotes NO debe mover retroactivamente los 3.000 de
        // diferencial histórico. Esa es una decisión contable, no de un arranque.
        assertThat(like.isIssuanceSettled()).isTrue();
        verify(adLikeRepository).saveAll(any());
    }

    @Test
    @DisplayName("like sin sesión LIKED: lo deja en NULL en vez de inventar un cero")
    void unresolvableLikeStaysNull() {
        AdLike like = legacyLike(10_000L);

        when(adLikeRepository.findWithoutCreditedAmount()).thenReturn(List.of(like));
        when(adWatchSessionRepository.findLikedSessions(42L, 900L)).thenReturn(List.of());
        when(surveyRewardRepository.findWithoutCreditedAmount()).thenReturn(List.of());

        runner().run(null);

        // Un cero haría que el usuario viera 0 en su histórico. NULL mantiene el
        // fallback a la base, que es el comportamiento previo.
        assertThat(like.getCreditedAmountCents()).isNull();
        assertThat(like.isIssuanceSettled()).isFalse();
    }

    @Test
    @DisplayName("idempotente: sin filas pendientes no toca nada")
    void isIdempotent() {
        when(adLikeRepository.findWithoutCreditedAmount()).thenReturn(List.of());
        when(surveyRewardRepository.findWithoutCreditedAmount()).thenReturn(List.of());

        runner().run(null);

        verify(adLikeRepository, never()).saveAll(any());
        verify(surveyRewardRepository, never()).saveAll(any());
        verify(keyTransactionRepository, never()).sumInteractionCreditByReference(any());
    }

    @Test
    @DisplayName("encuestas: deriva el referenceId igual que RewardService")
    void surveyReferenceIdMatchesRewardService() {
        SurveySession session = new SurveySession();
        session.setId(77L);
        SurveyReward reward = SurveyReward.builder()
                .session(session)
                .amountCents(2_000L)
                .status(SurveyReward.RewardStatus.PROCESSED)
                .build();

        UUID expectedRef = UUID.nameUUIDFromBytes("survey-session-77".getBytes());

        when(adLikeRepository.findWithoutCreditedAmount()).thenReturn(List.of());
        when(surveyRewardRepository.findWithoutCreditedAmount()).thenReturn(List.of(reward));
        when(keyTransactionRepository.sumInteractionCreditByReference(expectedRef)).thenReturn(1_400L);

        runner().run(null);

        ArgumentCaptor<UUID> ref = ArgumentCaptor.forClass(UUID.class);
        verify(keyTransactionRepository).sumInteractionCreditByReference(ref.capture());
        assertThat(ref.getValue()).isEqualTo(expectedRef);
        assertThat(reward.getCreditedAmountCents()).isEqualTo(1_400L);
        assertThat(reward.isIssuanceSettled()).isTrue();
    }
}
