package com.verygana2.config;

import java.util.List;
import java.util.UUID;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.verygana2.models.ads.AdLike;
import com.verygana2.models.ads.AdWatchSession;
import com.verygana2.models.surveys.SurveyReward;
import com.verygana2.repositories.AdLikeRepository;
import com.verygana2.repositories.AdWatchSessionRepository;
import com.verygana2.repositories.finance.KeyTransactionRepository;
import com.verygana2.repositories.surveys.SurveyRewardRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Rellena credited_amount en las filas anteriores a esa columna (likes y
 * recompensas de encuesta creados antes del 2026-07-14), reconstruyendo el monto
 * desde KeyTransaction, que es donde quedó registrado el crédito real.
 *
 * ES IDEMPOTENTE: solo toca filas con credited_amount NULL. Tras la primera
 * ejecución no encuentra nada y no hace nada. Mismo criterio que
 * TreasuryDataInitializer, que es la convención del proyecto para esto — no hay
 * Flyway, así que un ApplicationRunner idempotente es el único mecanismo que
 * garantiza "se ejecuta una vez" sin depender de que alguien se acuerde.
 *
 * NO MUEVE DINERO. Marca las filas rellenadas como issuance_settled = true para
 * que el job por lotes NO liquide retroactivamente su diferencial. Ese diferencial
 * histórico existe y está atrapado en KEYS_RESERVE, pero moverlo automáticamente
 * en un arranque sería una decisión contable tomada por un job. El runner lo
 * calcula y lo deja en el log para que una persona decida.
 */
@Slf4j
@Component
@Order(100) // después de TreasuryDataInitializer
@RequiredArgsConstructor
public class KeyIssuanceBackfillRunner implements ApplicationRunner {

    private final AdLikeRepository adLikeRepository;
    private final SurveyRewardRepository surveyRewardRepository;
    private final AdWatchSessionRepository adWatchSessionRepository;
    private final KeyTransactionRepository keyTransactionRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        long adDelta = backfillAdLikes();
        long surveyDelta = backfillSurveyRewards();

        long total = adDelta + surveyDelta;
        if (total != 0) {
            log.warn("=== [BACKFILL] Diferencial histórico NO liquidado: {} centavos "
                    + "(likes={}, encuestas={}). Está atrapado en KEYS_RESERVE sin reconocerse "
                    + "como ingreso de OPERATIONS. Requiere decisión humana: este runner no "
                    + "mueve dinero. ===", total, adDelta, surveyDelta);
        }
    }

    /** @return diferencial histórico (financiado − acreditado) de los likes rellenados. */
    private long backfillAdLikes() {
        List<AdLike> pending = adLikeRepository.findWithoutCreditedAmount();
        if (pending.isEmpty()) {
            return 0L;
        }

        long delta = 0;
        int resolved = 0;
        int unresolved = 0;

        for (AdLike like : pending) {
            List<AdWatchSession> sessions = adWatchSessionRepository.findLikedSessions(
                    like.getId().getConsumerId(), like.getId().getAdId());

            if (sessions.isEmpty()) {
                // Sin sesión LIKED no hay referenceId con el que llegar al crédito.
                // Se deja en NULL: las queries de "total ganado" caen a la base, que
                // es el comportamiento previo, en vez de reportar cero.
                unresolved++;
                continue;
            }

            long credited = 0;
            for (AdWatchSession session : sessions) {
                credited += keyTransactionRepository.sumInteractionCreditByReference(session.getId());
            }

            like.setCreditedAmountCents(credited);
            like.setIssuanceSettled(true); // histórico: no liquidar retroactivamente
            delta += like.getRewardAmount() - credited;
            resolved++;
        }

        adLikeRepository.saveAll(pending);
        log.info("[BACKFILL] Likes: {} rellenados, {} sin sesión LIKED (quedan en NULL), delta={} centavos",
                resolved, unresolved, delta);
        return delta;
    }

    /** @return diferencial histórico (financiado − acreditado) de las encuestas rellenadas. */
    private long backfillSurveyRewards() {
        List<SurveyReward> pending = surveyRewardRepository.findWithoutCreditedAmount();
        if (pending.isEmpty()) {
            return 0L;
        }

        long delta = 0;
        for (SurveyReward reward : pending) {
            // Misma derivación que RewardService.creditPoints: el referenceId no se
            // guardó en ningún lado, se recalcula desde el id de la sesión.
            UUID referenceId = UUID.nameUUIDFromBytes(
                    ("survey-session-" + reward.getSession().getId()).getBytes());
            long credited = keyTransactionRepository.sumInteractionCreditByReference(referenceId);

            reward.setCreditedAmountCents(credited);
            reward.setIssuanceSettled(true);
            delta += reward.getAmountCents() - credited;
        }

        surveyRewardRepository.saveAll(pending);
        log.info("[BACKFILL] Encuestas: {} rellenadas, delta={} centavos", pending.size(), delta);
        return delta;
    }
}
