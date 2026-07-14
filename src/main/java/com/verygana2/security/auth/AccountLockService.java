package com.verygana2.security.auth;

import java.time.Instant;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.verygana2.exceptions.authExceptions.AccountLockedException;
import com.verygana2.models.User;
import com.verygana2.repositories.UserRepository;
import com.verygana2.security.auth.refreshToken.SecurityAuditService;
import com.verygana2.services.interfaces.EmailVerificationService;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Bloqueo de cuenta por intentos fallidos de login consecutivos, distinto del
 * bloqueo de compliance/admin (UserState.BLOCKED): es automático, temporal y
 * se resuelve por el propio usuario con un código enviado a su correo — no
 * requiere intervención de soporte.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AccountLockService {

    private final UserRepository userRepository;
    private final EmailVerificationService emailVerificationService;
    private final SecurityAuditService securityAuditService;

    @Value("${app.security.account-lock.max-failed-attempts:5}")
    private int maxFailedAttempts;

    public boolean isLocked(String identifier) {
        return findUser(identifier)
                .map(user -> user.getAccountLockedAt() != null)
                .orElse(false);
    }

    /**
     * Registra un intento fallido (contraseña incorrecta) para una cuenta que
     * existe. Si cruza el umbral, bloquea la cuenta y envía el código de
     * desbloqueo. No hace nada si el identificador no corresponde a ningún
     * usuario (nada que contar ni bloquear).
     */
    public void registerFailedAttempt(String identifier) {
        findUser(identifier).ifPresent(user -> {
            user.setFailedLoginAttempts(user.getFailedLoginAttempts() + 1);
            boolean justLocked = user.getFailedLoginAttempts() >= maxFailedAttempts;

            if (justLocked) {
                user.setAccountLockedAt(Instant.now());
                user.setFailedLoginAttempts(0);
            }

            userRepository.save(user);

            if (justLocked) {
                log.warn("ACCOUNT LOCKED - User: {}, failed attempts reached: {}", user.getEmail(), maxFailedAttempts);
                securityAuditService.logSuspiciousActivity(
                        user.getEmail(),
                        "ACCOUNT_LOCKED_FAILED_ATTEMPTS",
                        String.format("Cuenta bloqueada tras %d intentos fallidos consecutivos", maxFailedAttempts)
                );
                emailVerificationService.sendVerificationCode(user.getEmail());
            }
        });
    }

    /** Se llama tras un login exitoso, para limpiar el contador. */
    public void registerSuccessfulLogin(String identifier) {
        findUser(identifier).ifPresent(user -> {
            if (user.getFailedLoginAttempts() > 0) {
                user.setFailedLoginAttempts(0);
                userRepository.save(user);
            }
        });
    }

    /** Reenvía el código de desbloqueo — no revela si la cuenta existe o no está bloqueada. */
    public void resendUnlockCode(String identifier) {
        findUser(identifier)
                .filter(user -> user.getAccountLockedAt() != null)
                .ifPresent(user -> emailVerificationService.sendVerificationCode(user.getEmail()));
    }

    /**
     * Verifica el código y, si es correcto, desbloquea la cuenta.
     * @throws com.verygana2.exceptions.EmailVerificationException si el código no coincide, expiró o se agotaron los intentos
     */
    public void unlock(String identifier, String code) {
        User user = findUser(identifier)
                .orElseThrow(() -> new EntityNotFoundException("Cuenta no encontrada"));

        if (user.getAccountLockedAt() == null) {
            throw new AccountLockedException("Esta cuenta no está bloqueada");
        }

        emailVerificationService.verifyCode(user.getEmail(), code);

        user.setAccountLockedAt(null);
        user.setFailedLoginAttempts(0);
        userRepository.save(user);

        log.info("Account unlocked for user: {}", user.getEmail());
    }

    private Optional<User> findUser(String identifier) {
        return userRepository.findByEmailOrPhoneNumber(identifier, identifier);
    }
}
