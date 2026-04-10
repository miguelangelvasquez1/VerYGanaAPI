package com.verygana2.utils.cron;

import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.verygana2.models.enums.raffles.RaffleStatus;
import com.verygana2.models.raffles.Raffle;
import com.verygana2.repositories.raffles.RaffleRepository;
import com.verygana2.services.interfaces.raffles.DrawingService;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class RaffleScheduler {

    private final RaffleRepository raffleRepository;
    private final DrawingService drawingService;
    private final ScheduledExecutorService precisionDrawExecutor;

    // IDs ya programados para sorteo — evita programar dos veces la misma rifa
    private final Set<Long> scheduledDraws   = ConcurrentHashMap.newKeySet();

    // IDs cuyo sorteo ya está corriendo
    private final Set<Long> drawsInProgress = ConcurrentHashMap.newKeySet();

    public RaffleScheduler (RaffleRepository raffleRepository, DrawingService drawingService, @Qualifier("precisionDrawExecutor") ScheduledExecutorService precisionDrawExecutor){
        this.raffleRepository = raffleRepository;
        this.drawingService = drawingService;
        this.precisionDrawExecutor = precisionDrawExecutor;
    }

    @Scheduled(cron = "0 * * * * *")
    @Transactional
    public void processRaffleStateTransitions() {
        log.debug("🔄 Running raffle state transition check...");

        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);

        // Procesar cada tipo de transición
        int activated = activateDraftRaffles(now);
        int closed = closeActiveRaffles(now);
        int live = setLiveRaffles(now);
        int scheduled = scheduleUpcomingDraws(now);

        if (activated > 0 || closed > 0 || live > 0) {
            log.info("📊 State transitions: {} activated, {} closed, {} live, {} draws scheduled",
                    activated, closed, live, scheduled);
        }
    }

    /**
     * Busca rifas LIVE con drawDate en los próximos 65 segundos
     * y las programa para ejecutarse en el momento exacto.
     * El margen de 65s garantiza que ninguna rifa se pierda entre
     * dos ciclos del scheduler de 60s.
     */
    private int scheduleUpcomingDraws(ZonedDateTime now) {
        // Buscar rifas cuya drawDate está entre ahora y 65 segundos adelante
        ZonedDateTime horizon = now.plusSeconds(65);
        List<Raffle> upcoming = raffleRepository.findLiveRafflesWithDrawDateBefore(horizon);
        int scheduled = 0;

        for (Raffle raffle : upcoming) {
            Long raffleId = raffle.getId();

            // Saltar si ya fue programada o está en progreso
            if (scheduledDraws.contains(raffleId) || drawsInProgress.contains(raffleId)) {
                continue;
            }

            long delayMs = ChronoUnit.MILLIS.between(now, raffle.getDrawDate());

            // Si la drawDate ya pasó (retraso del scheduler) ejecutar en 500ms
            long safeDelay = Math.max(500, delayMs);

            scheduledDraws.add(raffleId);

            precisionDrawExecutor.schedule(
                () -> executeDrawAsync(raffleId),
                safeDelay,
                TimeUnit.MILLISECONDS
            );

            log.info("⏱️ Draw for raffle {} scheduled in {}ms (drawDate: {})",
                    raffleId, safeDelay, raffle.getDrawDate());
            scheduled++;
        }

        return scheduled;
    }

    // @Async para no bloquear el hilo del scheduler
    // Usa el drawRevealExecutor que ya configuraste en AsyncConfig
    @Async("drawRevealExecutor")
    public void executeDrawAsync(Long raffleId) {
        try {
            log.info("Starting automatic draw for raffle {}", raffleId);
            drawingService.conductDraw(raffleId);
            log.info("✅ Automatic draw completed for raffle {}", raffleId);
        } catch (Exception e) {
            log.error("Automatic draw failed for raffle {}: {}", raffleId, e.getMessage(), e);
        } finally {
            // Siempre limpiar el set, aunque falle
            drawsInProgress.remove(raffleId);
        }
    }

    private int activateDraftRaffles(ZonedDateTime now) {
        List<Raffle> raffles = raffleRepository.findRafflesToActivate(now);
        int activated = 0;
        for (Raffle raffle : raffles) {
            try {
                log.debug("Activating raffle: {} (ID: {})", raffle.getTitle(), raffle.getId());

                raffle.setRaffleStatus(RaffleStatus.ACTIVE);
                raffleRepository.save(raffle);
                activated++;
                log.info("Raffle {} activated successfully", raffle.getId());
            } catch (Exception e) {
                log.error("Error activating raffle {}: {}", raffle.getId(), e.getMessage(), e);
            }
        }

        return activated;
    }

    private int closeActiveRaffles(ZonedDateTime now) {
        List<Raffle> raffles = raffleRepository.findRafflesToClose(now);
        int closed = 0;
        for (Raffle raffle : raffles) {
            try {
                log.debug("Closing raffle: {} (ID: {})", raffle.getTitle(), raffle.getId());

                raffle.setRaffleStatus(RaffleStatus.CLOSED);
                raffleRepository.save(raffle);
                closed++;
                log.info("✅ Raffle {} closed successfully. Total tickets: {}",
                        raffle.getId(), raffle.getTotalTicketsIssued());

            } catch (Exception e) {
                log.error("Error closing raffle {}: {}", raffle.getId(), e.getMessage(), e);
            }
        }

        return closed;
    }

    private int setLiveRaffles(ZonedDateTime now) {
    ZonedDateTime windowStart = now.minusMinutes(30);
    ZonedDateTime liveThreshold = now.plusMinutes(60);

    // 🔍 DIAGNÓSTICO - quitar después
    log.info("now={} | windowStart={} | liveThreshold={}", now, windowStart, liveThreshold);
    List<Raffle> found = raffleRepository.findRafflesToSetLive(windowStart, liveThreshold);
    log.info("Raffles found for LIVE: {}", found.size());
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
