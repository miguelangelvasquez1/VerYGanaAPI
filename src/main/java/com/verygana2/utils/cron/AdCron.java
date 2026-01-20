package com.verygana2.utils.cron;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.verygana2.repositories.AdWatchSessionRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RequiredArgsConstructor
@Component
@Slf4j
public class AdCron { // Si se puede evitar este cron mejor

    private final AdWatchSessionRepository adWatchSessionRepository;
    
    @Scheduled(cron = "0 * * * * *") // cada minuto exacto
    @Transactional
    public void expireSessions() {
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        int updated = adWatchSessionRepository.expireActiveSessions(now);
        log.debug("Expired {} ad watch sessions", updated);
    }
}
