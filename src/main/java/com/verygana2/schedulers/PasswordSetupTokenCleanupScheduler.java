package com.verygana2.schedulers;

import java.time.Instant;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.verygana2.repositories.PasswordSetupTokenRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class PasswordSetupTokenCleanupScheduler {

    private final PasswordSetupTokenRepository tokenRepository;

    @Scheduled(cron = "0 0 3 * * ?")
    @Transactional
    public void cleanupExpiredTokens() {
        log.info("Running password setup token cleanup");
        tokenRepository.deleteByExpiresAtBefore(Instant.now());
    }
}
