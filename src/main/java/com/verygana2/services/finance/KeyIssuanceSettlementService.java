package com.verygana2.services.finance;

import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.verygana2.models.records.IssuanceTotals;
import com.verygana2.repositories.AdLikeRepository;
import com.verygana2.repositories.surveys.SurveyRewardRepository;
import com.verygana2.services.interfaces.finance.TreasuryService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Liquida en tesorería el diferencial que abre el multiplicador de nivel entre
 * lo que el anunciante financió y lo que se le acreditó al consumidor.
 *
 * POR QUÉ POR LOTES Y NO EN CADA INTERACCIÓN:
 * cada liquidación toma lock pesimista sobre KEYS_RESERVE y OPERATIONS. Hacerlo
 * dentro del like serializaba TODOS los likes de la plataforma contra dos filas
 * globales, y los likes son el evento de dinero más frecuente de la app. El
 * diferencial no se pierde: AdLike y SurveyReward ya guardan lo financiado y lo
 * acreditado por fila, así que el job lo reconstruye sumando.
 *
 * El corte (cutoff) se toma una vez y se usa tanto para sumar como para marcar,
 * de modo que una interacción que entre mientras el job corre queda para el
 * siguiente ciclo en vez de marcarse liquidada sin haberse sumado.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KeyIssuanceSettlementService {

    private final AdLikeRepository adLikeRepository;
    private final SurveyRewardRepository surveyRewardRepository;
    private final TreasuryService treasuryService;
    private final Clock clock;

    @Transactional
    public void settlePendingIssuance() {
        ZonedDateTime cutoff = ZonedDateTime.now(clock);
        UUID batchId = UUID.randomUUID();

        settleAdLikes(cutoff, batchId);
        settleSurveyRewards(cutoff, batchId);
    }

    private void settleAdLikes(ZonedDateTime cutoff, UUID batchId) {
        IssuanceTotals totals = adLikeRepository.sumUnsettledIssuance(cutoff);
        if (totals == null || totals.isEmpty()) {
            log.debug("[ISSUANCE-SETTLEMENT] Sin likes pendientes de liquidar.");
            return;
        }

        treasuryService.settleKeyIssuance(
                totals.fundedCents(), totals.creditedCents(), batchId, "AD_LIKE_BATCH");
        int marked = adLikeRepository.markIssuanceSettled(cutoff);

        log.info("[ISSUANCE-SETTLEMENT] Likes: {} filas, financiado={} acreditado={} delta={} batch={}",
                marked, totals.fundedCents(), totals.creditedCents(), totals.deltaCents(), batchId);
    }

    private void settleSurveyRewards(ZonedDateTime cutoff, UUID batchId) {
        IssuanceTotals totals = surveyRewardRepository.sumUnsettledIssuance(cutoff);
        if (totals == null || totals.isEmpty()) {
            log.debug("[ISSUANCE-SETTLEMENT] Sin recompensas de encuesta pendientes de liquidar.");
            return;
        }

        treasuryService.settleKeyIssuance(
                totals.fundedCents(), totals.creditedCents(), batchId, "SURVEY_REWARD_BATCH");
        int marked = surveyRewardRepository.markIssuanceSettled(cutoff);

        log.info("[ISSUANCE-SETTLEMENT] Encuestas: {} filas, financiado={} acreditado={} delta={} batch={}",
                marked, totals.fundedCents(), totals.creditedCents(), totals.deltaCents(), batchId);
    }
}
