package com.verygana2.utils.cron;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.verygana2.repositories.NotificationRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationCleanupTask {

    private final NotificationRepository notificationRepository;

    // Ejecuta cada día a las 3:00 AM hora Colombia (8:00 AM UTC)
    @Scheduled(cron = "0 0 8 * * *", zone = "UTC")
    @Transactional
    public void deleteOldReadNotifications() {
        ZonedDateTime cutoff = ZonedDateTime.now(ZoneOffset.UTC).minusDays(30);
        int deleted = notificationRepository.deleteByIsReadTrueAndCreatedAtBefore(cutoff);
        log.info("🧹 Limpieza de notificaciones: {} eliminadas (anteriores a {})", deleted, cutoff.toLocalDate());
    }
}
