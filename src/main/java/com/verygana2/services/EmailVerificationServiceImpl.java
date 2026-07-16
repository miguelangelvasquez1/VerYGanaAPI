package com.verygana2.services;

import java.security.SecureRandom;
import java.time.LocalDateTime;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.verygana2.exceptions.EmailVerificationException;
import com.verygana2.models.EmailVerificationCode;
import com.verygana2.repositories.EmailVerificationCodeRepository;
import com.verygana2.services.interfaces.EmailService;
import com.verygana2.services.interfaces.EmailVerificationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailVerificationServiceImpl implements EmailVerificationService {

    private static final int CODE_MAX_VALUE = 1_000_000;
    private static final int EXPIRATION_MINUTES = 10;
    private static final int MAX_ATTEMPTS = 5;

    /** Rate-limiting: mínimo entre envíos y tope de códigos por hora por email */
    private static final int RESEND_COOLDOWN_SECONDS = 60;
    private static final int MAX_CODES_PER_HOUR = 5;

    private final EmailVerificationCodeRepository emailVerificationCodeRepository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    @Transactional
    public void sendVerificationCode(String email) {
        String code = issueCode(email);
        log.info("Verification code generated for {}", email);
        emailService.sendVerificationCodeEmail(email, code);
    }

    @Override
    @Transactional
    public void sendPasswordResetCode(String email) {
        String code = issueCode(email);
        log.info("Password reset code generated for {}", email);
        emailService.sendPasswordResetEmail(email, code);
    }

    /**
     * Genera y persiste un código nuevo aplicando rate-limit e invalidando
     * códigos anteriores sin borrar el historial de la última hora
     * (el historial sostiene el tope de MAX_CODES_PER_HOUR).
     */
    private String issueCode(String email) {
        enforceRateLimit(email);

        String code = generateCode();

        emailVerificationCodeRepository.findTopByEmailOrderByIdDesc(email)
                .filter(prev -> !prev.isUsed())
                .ifPresent(prev -> {
                    prev.setUsed(true);
                    emailVerificationCodeRepository.save(prev);
                });
        emailVerificationCodeRepository.deleteByEmailAndCreatedAtBefore(
                email, LocalDateTime.now().minusHours(1));

        emailVerificationCodeRepository.save(EmailVerificationCode.builder()
                .email(email)
                .codeHash(passwordEncoder.encode(code))
                .expiresAt(LocalDateTime.now().plusMinutes(EXPIRATION_MINUTES))
                .used(false)
                .attempts(0)
                .build());

        return code;
    }

    private void enforceRateLimit(String email) {
        LocalDateTime now = LocalDateTime.now();

        emailVerificationCodeRepository.findTopByEmailOrderByIdDesc(email)
                .filter(prev -> prev.getCreatedAt().isAfter(now.minusSeconds(RESEND_COOLDOWN_SECONDS)))
                .ifPresent(prev -> {
                    throw new EmailVerificationException(
                            "Espera un momento antes de solicitar otro código");
                });

        long sentLastHour = emailVerificationCodeRepository
                .countByEmailAndCreatedAtAfter(email, now.minusHours(1));
        if (sentLastHour >= MAX_CODES_PER_HOUR) {
            throw new EmailVerificationException(
                    "Has solicitado demasiados códigos. Intenta de nuevo en una hora");
        }
    }

    @Override
    @Transactional
    public void verifyCode(String email, String code) {
        EmailVerificationCode record = emailVerificationCodeRepository.findTopByEmailOrderByIdDesc(email)
                .orElseThrow(() -> new EmailVerificationException("No se ha solicitado un código de verificación para este correo"));

        if (record.isUsed()) {
            throw new EmailVerificationException("Este código ya fue utilizado");
        }
        if (LocalDateTime.now().isAfter(record.getExpiresAt())) {
            throw new EmailVerificationException("El código ha expirado. Solicita uno nuevo");
        }
        if (record.getAttempts() >= MAX_ATTEMPTS) {
            throw new EmailVerificationException("Se agotaron los intentos para este código. Solicita uno nuevo");
        }

        if (!passwordEncoder.matches(code, record.getCodeHash())) {
            record.setAttempts(record.getAttempts() + 1);
            emailVerificationCodeRepository.save(record);
            throw new EmailVerificationException("Código de verificación incorrecto");
        }

        record.setUsed(true);
        emailVerificationCodeRepository.save(record);
        log.info("Email verified for {}", email);
    }

    private String generateCode() {
        return String.format("%06d", secureRandom.nextInt(CODE_MAX_VALUE));
    }
}
