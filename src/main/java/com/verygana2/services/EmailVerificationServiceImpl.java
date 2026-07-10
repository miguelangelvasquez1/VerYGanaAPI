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

    private final EmailVerificationCodeRepository emailVerificationCodeRepository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    @Transactional
    public void sendVerificationCode(String email) {
        String code = generateCode();

        emailVerificationCodeRepository.deleteByEmail(email);
        emailVerificationCodeRepository.save(EmailVerificationCode.builder()
                .email(email)
                .codeHash(passwordEncoder.encode(code))
                .expiresAt(LocalDateTime.now().plusMinutes(EXPIRATION_MINUTES))
                .used(false)
                .attempts(0)
                .build());

        log.info("Verification code generated for {}", email);
        emailService.sendVerificationCodeEmail(email, code);
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
