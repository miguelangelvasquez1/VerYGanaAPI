package com.verygana2.schedulers;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.verygana2.services.interfaces.raffles.RaffleWinnerService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Marca como EXPIRED los premios PENDING cuyos ganadores no reclamaron
 * dentro del claim_deadline (RaffleWinner). No elimina registros: se
 * conserva el historial de premios ganados y no reclamados.
 *
 * Configurable en application.yml:
 *   prize.expiry.cron: "0 0/30 * * * *" (default cada 30 min)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PrizeExpiryScheduler {

    private final RaffleWinnerService raffleWinnerService;

    @Scheduled(cron = "${prize.expiry.cron:0 0/30 * * * *}")
    public void expireOverduePrizes() {
        log.debug("[SCHEDULER] Buscando premios PENDING con claim deadline vencido...");
        int expired = raffleWinnerService.expireOverduePrizes();
        if (expired > 0) {
            log.info("[SCHEDULER] {} premio(s) marcados como EXPIRED", expired);
        }
    }
}
