package com.verygana2.services.gameDesigner;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.verygana2.exceptions.authExceptions.InvalidTokenException;
import com.verygana2.models.PasswordSetupToken;
import com.verygana2.models.User;
import com.verygana2.models.enums.UserState;
import com.verygana2.repositories.PasswordSetupTokenRepository;
import com.verygana2.repositories.UserRepository;
import com.verygana2.services.interfaces.EmailService;
import com.verygana2.services.interfaces.PasswordSetupService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class PasswordSetupServiceImpl implements PasswordSetupService {

    private final PasswordSetupTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Value("${app.password-setup-token.expiration-hours:72}")
    private int expirationHours;

    @Override
    @Transactional
    public void initiatePasswordSetup(User user, String designerName, String designerCode) {
        tokenRepository.deleteByUser(user);

        String rawToken = generateSecureToken();

        PasswordSetupToken setupToken = new PasswordSetupToken();
        setupToken.setToken(rawToken);
        setupToken.setUser(user);
        setupToken.setExpiresAt(Instant.now().plus(expirationHours, ChronoUnit.HOURS));
        tokenRepository.save(setupToken);

        String setupLink = frontendUrl + "/game-designer/setup-password?token=" + rawToken;
        emailService.sendDesignerPasswordSetupEmail(user.getEmail(), designerName, setupLink, designerCode);

        log.info("Password setup token created for user ID: {}", user.getId());
    }

    @Override
    @Transactional
    public void completePasswordSetup(String rawToken, String newPassword) {
        PasswordSetupToken setupToken = tokenRepository.findByToken(rawToken)
                .orElseThrow(() -> new InvalidTokenException("Invalid or expired password setup token"));

        if (setupToken.isUsed()) {
            throw new InvalidTokenException("This setup link has already been used");
        }

        if (setupToken.getExpiresAt().isBefore(Instant.now())) {
            throw new InvalidTokenException("This setup link has expired. Please contact an administrator");
        }

        User user = setupToken.getUser();
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setPasswordConfigured(true);
        user.setUserState(UserState.ACTIVE);
        userRepository.save(user);

        setupToken.setUsed(true);
        tokenRepository.save(setupToken);

        log.info("Password configured for user ID: {}", user.getId());
    }

    private String generateSecureToken() {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
