package com.verygana2.services.finance;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.verygana2.models.records.IssuanceTotals;
import com.verygana2.repositories.AdLikeRepository;
import com.verygana2.repositories.surveys.SurveyRewardRepository;
import com.verygana2.services.interfaces.finance.TreasuryService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * El diferencial del multiplicador se liquida por lotes, no en cada like: hacerlo
 * inline serializaba todos los likes contra las filas de KEYS_RESERVE y OPERATIONS.
 * Lo que se prueba aquí es que el lote no pierda ni duplique ese diferencial.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("KeyIssuanceSettlementService")
class KeyIssuanceSettlementServiceTest {

    @Mock AdLikeRepository adLikeRepository;
    @Mock SurveyRewardRepository surveyRewardRepository;
    @Mock TreasuryService treasuryService;

    private final Clock clock = Clock.fixed(Instant.parse("2026-09-16T10:00:00Z"), ZoneOffset.UTC);

    private KeyIssuanceSettlementService service() {
        return new KeyIssuanceSettlementService(
                adLikeRepository, surveyRewardRepository, treasuryService, clock);
    }

    @Test
    @DisplayName("agrega los likes del período en UNA sola liquidación")
    void aggregatesAdLikesIntoSingleSettlement() {
        // 100 likes de 10.000 financiados y 7.000 acreditados cada uno.
        when(adLikeRepository.sumUnsettledIssuance(any()))
                .thenReturn(new IssuanceTotals(1_000_000L, 700_000L));
        when(surveyRewardRepository.sumUnsettledIssuance(any()))
                .thenReturn(new IssuanceTotals(0L, 0L));

        service().settlePendingIssuance();

        verify(treasuryService).settleKeyIssuance(
                eq(1_000_000L), eq(700_000L), any(UUID.class), eq("AD_LIKE_BATCH"));
        verify(adLikeRepository).markIssuanceSettled(any());
    }

    @Test
    @DisplayName("el corte de suma y el de marcado son el MISMO: una interacción en vuelo no se pierde")
    void sumAndMarkShareTheSameCutoff() {
        when(adLikeRepository.sumUnsettledIssuance(any()))
                .thenReturn(new IssuanceTotals(1_000L, 500L));
        when(surveyRewardRepository.sumUnsettledIssuance(any()))
                .thenReturn(new IssuanceTotals(0L, 0L));

        service().settlePendingIssuance();

        ArgumentCaptor<ZonedDateTime> sumCutoff = ArgumentCaptor.forClass(ZonedDateTime.class);
        ArgumentCaptor<ZonedDateTime> markCutoff = ArgumentCaptor.forClass(ZonedDateTime.class);
        verify(adLikeRepository).sumUnsettledIssuance(sumCutoff.capture());
        verify(adLikeRepository).markIssuanceSettled(markCutoff.capture());

        // Con cortes distintos, una fila insertada entre ambos quedaría marcada
        // como liquidada sin haberse sumado nunca: diferencial perdido para siempre.
        assertThat(markCutoff.getValue()).isEqualTo(sumCutoff.getValue());
    }

    @Test
    @DisplayName("nada pendiente: ni liquida ni marca (no ensucia el libro contable)")
    void nothingPending_doesNothing() {
        when(adLikeRepository.sumUnsettledIssuance(any())).thenReturn(new IssuanceTotals(0L, 0L));
        when(surveyRewardRepository.sumUnsettledIssuance(any())).thenReturn(new IssuanceTotals(0L, 0L));

        service().settlePendingIssuance();

        verify(treasuryService, never()).settleKeyIssuance(anyLong(), anyLong(), any(), any());
        verify(adLikeRepository, never()).markIssuanceSettled(any());
        verify(surveyRewardRepository, never()).markIssuanceSettled(any());
    }

    @Test
    @DisplayName("likes y encuestas se liquidan por separado, con su propio referenceType")
    void adsAndSurveysSettleSeparately() {
        when(adLikeRepository.sumUnsettledIssuance(any()))
                .thenReturn(new IssuanceTotals(1_000L, 700L));
        when(surveyRewardRepository.sumUnsettledIssuance(any()))
                .thenReturn(new IssuanceTotals(2_000L, 1_400L));

        service().settlePendingIssuance();

        verify(treasuryService).settleKeyIssuance(eq(1_000L), eq(700L), any(), eq("AD_LIKE_BATCH"));
        verify(treasuryService).settleKeyIssuance(eq(2_000L), eq(1_400L), any(), eq("SURVEY_REWARD_BATCH"));
    }

    @Test
    @DisplayName("si tesorería falla, NO marca las filas: el diferencial se reintenta en el siguiente ciclo")
    void treasuryFailure_doesNotMarkRows() {
        when(adLikeRepository.sumUnsettledIssuance(any()))
                .thenReturn(new IssuanceTotals(1_000L, 700L));
        org.mockito.Mockito.doThrow(new IllegalStateException("KEYS_RESERVE insuficiente"))
                .when(treasuryService).settleKeyIssuance(anyLong(), anyLong(), any(), any());

        assertThatThrownBy(() -> service().settlePendingIssuance())
                .isInstanceOf(IllegalStateException.class);

        verify(adLikeRepository, never()).markIssuanceSettled(any());
    }
}
