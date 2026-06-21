package com.verygana2.schedulers;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.verygana2.services.interfaces.finance.KeyExpiryService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Procesa llaves vencidas a las 00:05 AM Colombia (05:05 AM UTC).
 *
 * Los 5 minutos de margen aseguran que las llaves cuyo expires_at es exactamente
 * medianoche Colombia ya sean visibles con expiredAt < NOW() en la query.
 *
 * - Purchase keys: vencen el día 1 del mes siguiente a las 00:00 Colombia.
 * - Connectivity keys: vencen al día siguiente a las 00:00 Colombia.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KeyExpiryScheduler {

    private final KeyExpiryService keyExpiryService;

    @Scheduled(cron = "0 5 5 * * *")
    public void processExpiredKeys() {
        log.info("[KEY-EXPIRY-SCHEDULER] Iniciando procesamiento de llaves vencidas...");
        keyExpiryService.processExpiredKeys();
        log.info("[KEY-EXPIRY-SCHEDULER] Procesamiento completado.");
    }
}
