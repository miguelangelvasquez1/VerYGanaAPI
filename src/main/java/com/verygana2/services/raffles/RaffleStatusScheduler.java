package com.verygana2.services.raffles;

import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.verygana2.models.enums.raffles.RaffleStatus;
import com.verygana2.models.raffles.Raffle;
import com.verygana2.repositories.raffles.RaffleRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class RaffleStatusScheduler {

    private final RaffleRepository raffleRepository;

    @Scheduled(cron = "0 * * * * *")
    @Transactional
    public void processRaffleStateTransitions() {
        log.debug("🔄 Running raffle state transition check...");

        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);

        // Procesar cada tipo de transición
        int activated = activateDraftRaffles(now);
        int closed = closeActiveRaffles(now);
        int live = setLiveRaffles(now);

        if (activated > 0 || closed > 0 || live > 0) {
            log.info("📊 State transitions: {} activated, {} closed, {} set to live",
                    activated, closed, live);
        }
    }

    private int activateDraftRaffles(ZonedDateTime now) {
        List<Raffle> raffles = raffleRepository.findRafflesToActivate(now);
        int activated = 0;
        for (Raffle raffle : raffles) {
            try {
                log.debug("🟢 Activating raffle: {} (ID: {})", raffle.getTitle(), raffle.getId());

                raffle.setRaffleStatus(RaffleStatus.ACTIVE);
                raffleRepository.save(raffle);
                activated++;
                log.info("✅ Raffle {} activated successfully", raffle.getId());
            } catch (Exception e) {
                log.error("❌ Error activating raffle {}: {}", raffle.getId(), e.getMessage(), e);
            }
        }

        return activated;
    }

    private int closeActiveRaffles(ZonedDateTime now) {
        List<Raffle> raffles = raffleRepository.findRafflesToClose(now);
        int closed = 0;
        for (Raffle raffle : raffles) {
            try {
                log.debug("🔴 Closing raffle: {} (ID: {})", raffle.getTitle(), raffle.getId());

                raffle.setRaffleStatus(RaffleStatus.CLOSED);
                raffleRepository.save(raffle);
                closed++;
                log.info("✅ Raffle {} closed successfully. Total tickets: {}",
                        raffle.getId(), raffle.getTotalTicketsIssued());

            } catch (Exception e) {
                log.error("❌ Error closing raffle {}: {}", raffle.getId(), e.getMessage(), e);
            }
        }

        return closed;
    }

    private int setLiveRaffles(ZonedDateTime now) {
    ZonedDateTime windowStart = now.minusMinutes(30);
    ZonedDateTime liveThreshold = now.plusMinutes(60);

    // 🔍 DIAGNÓSTICO - quitar después
    log.info("⏰ now={} | windowStart={} | liveThreshold={}", now, windowStart, liveThreshold);
    List<Raffle> found = raffleRepository.findRafflesToSetLive(windowStart, liveThreshold);
    log.info("🔍 Raffles encontradas para LIVE: {}", found.size());
    found.forEach(r -> log.info("  → Raffle {} | status={} | drawDate={}", 
        r.getId(), r.getRaffleStatus(), r.getDrawDate()));

    List<Raffle> raffles = raffleRepository.findRafflesToSetLive(windowStart, liveThreshold);
    int lives = 0;

    for (Raffle raffle : raffles) {
        try {
            log.debug("🔴🔵 Setting raffle to LIVE: {} (ID: {})", raffle.getTitle(), raffle.getId());
            raffle.setRaffleStatus(RaffleStatus.LIVE);
            raffleRepository.save(raffle);
            lives++;
            log.debug("✅ Raffle {} is now LIVE. Draw date: {}", raffle.getId(), raffle.getDrawDate());
        } catch (Exception e) {
            log.error("❌ Error setting raffle {} to LIVE: {}", raffle.getId(), e.getMessage(), e);
        }
    }

    return lives;
}

    /**
     * Log de resumen diario (ejecuta a las 8:00 AM)
     */
    @Scheduled(cron = "0 0 8 * * *")
    public void dailySummary() {
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("America/Bogota"));

        long active = raffleRepository.countByRaffleStatus(RaffleStatus.ACTIVE);
        long closed = raffleRepository.countByRaffleStatus(RaffleStatus.CLOSED);
        long live = raffleRepository.countByRaffleStatus(RaffleStatus.LIVE);

        log.debug("📊 Daily Raffle Summary ({}): {} active, {} closed, {} live",
                now.toLocalDate(), active, closed, live);
    }

}
