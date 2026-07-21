package com.verygana2.services;

import com.verygana2.models.Avatar;
import com.verygana2.models.Municipality;
import com.verygana2.models.enums.UserState;
import com.verygana2.services.interfaces.*;
import com.verygana2.services.interfaces.compliance.ScreeningService;
import com.verygana2.services.interfaces.levels.LevelService;

import org.hibernate.ObjectNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.verygana2.dtos.user.CommercialRegisterDTO;
import com.verygana2.dtos.user.ComplianceOfficerRegisterDTO;
import com.verygana2.dtos.user.ConsumerRegisterDTO;
import com.verygana2.dtos.user.GameDesignerRegisterDTO;
import com.verygana2.mappers.UserMapper;
import com.verygana2.models.User;
import com.verygana2.models.userDetails.CommercialDetails;
import com.verygana2.models.userDetails.ComplianceOfficerDetails;
import com.verygana2.models.userDetails.ConsumerDetails;
import com.verygana2.models.userDetails.GameDesignerDetails;
import com.verygana2.models.commercial.CommercialOnboarding;
import com.verygana2.repositories.UserRepository;
import com.verygana2.services.interfaces.finance.KeyWalletService;
import com.verygana2.utils.generators.UserHashGenerator;

import jakarta.validation.ValidationException;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.time.Period;

@Slf4j
@Transactional
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final ReferralService referralService;
    private final AvatarService avatarService;
    private final UserHashGenerator userHashGenerator;
    private final UserRepository userRepository;
    private final KeyWalletService keyWalletService;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;
    private final OutboxService outboxService;
    private final LocationService locationService;
    private final LevelService levelService;
    private final PasswordSetupService passwordSetupService;
    private final EmailVerificationService emailVerificationService;
    private final ScreeningService screeningService;
    private final TwilioSmsService twilioSmsService;
    private final com.verygana2.security.auth.refreshToken.RefreshTokenRepository refreshTokenRepository;

    @Override
    public User registerGameDesigner(GameDesignerRegisterDTO dto) {
        validateEmailAndPhoneNumber(dto.getEmail(), dto.getPhoneNumber());

        User user = userMapper.toUser(dto);
        user.setPassword(passwordEncoder.encode(UUID.randomUUID() + UUID.randomUUID().toString()));
        user.setPasswordConfigured(false);

        GameDesignerDetails details = userMapper.toGameDesignerDetails(dto);
        details.setDesignerCode("GD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        details.setJoinedAt(LocalDate.now());
        details.setUser(user);
        user.setUserDetails(details);

        User savedUser = userRepository.save(user);
        passwordSetupService.initiatePasswordSetup(savedUser, dto.getName(), details.getDesignerCode());

        return savedUser;
    }

    @Override
    public User registerComplianceOfficer(ComplianceOfficerRegisterDTO dto) {
        validateEmailAndPhoneNumber(dto.getEmail(), dto.getPhoneNumber());

        User user = userMapper.toUser(dto);
        user.setPassword(passwordEncoder.encode(dto.getPassword()));

        ComplianceOfficerDetails details = userMapper.toComplianceOfficerDetails(dto);
        details.setBadgeNumber("CO-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        details.setUser(user);
        user.setUserDetails(details);

        return userRepository.save(user);
    }

    /**
     * Registro básico (paso 1): solo email/password/phoneNumber.
     * La identificación jurídica (razón social, NIT, representante legal, municipio, etc.) y el
     * screening SAGRILAFT correspondiente se completan en el paso 3 del onboarding
     * (ver {@link com.verygana2.services.commercial.CommercialOnboardingServiceImpl#submitLegalIdentification}),
     * una vez esos datos existen — por eso aquí no hay lógica de PEP ni de screening.
     */
    public User registerCommercial(CommercialRegisterDTO dto) {
        validateEmailAndPhoneNumber(dto.getEmail(), dto.getPhoneNumber());

        User user = userMapper.toUser(dto);
        user.setPassword(passwordEncoder.encode(dto.getPassword()));

        CommercialDetails details = userMapper.toCommercialDetails(dto);
        details.setUser(user);

        user.setUserDetails(details);

        CommercialOnboarding onboarding = new CommercialOnboarding();
        onboarding.setCommercialDetails(details);
        details.setOnboarding(onboarding);

        // user -> details -> onboarding se persisten en cascada (CascadeType.ALL en
        // ambos saltos), por eso un solo save alcanza; guardar onboarding aparte antes
        // de esto fallaba con TransientPropertyValueException porque details todavía
        // no tenía id asignado.
        User savedUser = userRepository.save(user);

        sendVerificationEmail(savedUser);
        return savedUser;
    }

    @Override
    public User registerConsumer(ConsumerRegisterDTO dto) {
        validateEmailAndPhoneNumber(dto.getEmail(), dto.getPhoneNumber());

        User user = userMapper.toUser(dto);
        user.setPassword(passwordEncoder.encode(dto.getPassword()));

        ConsumerDetails details = userMapper.toConsumerDetails(dto);
        details.setUser(user);
        user.setUserDetails(details);

        // === ASIGNACIÓN DEL MUNICIPIO ===
        if (dto.getMunicipalityCode() != null) {
            Municipality municipality = locationService.getMunicipalityEntityByCode(dto.getMunicipalityCode());

            details.setMunicipality(municipality);
            details.setMunicipalityName(municipality.getName());   // por redundancia y consultas rápidas
            details.setDepartmentName(municipality.getDepartment().getName()); // por redundancia y consultas rápidas
        }

        Avatar avatar = avatarService.getActiveAvatarOrThrow(dto.getAvatarId());
        details.setAvatar(avatar);
        details.setUserName(normalizeUsername(dto.getUserName()));
        details.setGender(dto.getGender());
        details.setAge(calculateAge(dto.getBirthDate()));

        referralService.prepareNewConsumer(user, details, dto.getReferredByCode());

        if (Boolean.TRUE.equals(dto.getIsPEP())) {
            user.setUserState(UserState.PENDING_KYC_REVIEW);
        }

        // userHash NOT NULL+UNIQUE requiere un valor único antes del INSERT (IDENTITY);
        // se reemplaza con el hash real tras obtener el ID generado por la BD.
        details.setUserHash(UUID.randomUUID().toString());
        userRepository.saveAndFlush(user);
        details.setUserHash(userHashGenerator.generate(user.getId()));

        // Screening contra listas restrictivas — lanza ScreeningHitException si HIT (rollback)
        screeningService.screenOrThrow(
                user.getId(),
                details.getName() + " " + details.getLastName(),
                details.getDocumentNumber());

        keyWalletService.createFor(user.getId());
        levelService.initializeProfile(user.getId());

        if (details.getReferredBy() != null) {
            outboxService.saveReferralEvent(
                    details.getReferredBy().getId(),
                    details.getId()
            );
        }

        if (Boolean.TRUE.equals(dto.getIsPEP())) {
            log.info("Usuario {} marcado como PEP. Cuenta en revisión manual (PENDING_KYC_REVIEW).", user.getEmail());
        } else {
            sendVerificationEmail(user);
        }
        return user;
    }

    @Override
    public void verifyEmailCode(String email, String code) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("No existe una cuenta con ese correo"));

        if (user.getUserState() != UserState.PENDING_EMAIL) {
            throw new IllegalStateException("La cuenta ya está activa");
        }

        emailVerificationService.verifyCode(email, code);

        user.setUserState(UserState.ACTIVE);
        userRepository.save(user);

        log.info("Email verified for user {}", user.getEmail());
    }

    @Override
    public void resendVerificationEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("No existe una cuenta con ese correo"));

        if (user.getUserState() != UserState.PENDING_EMAIL) {
            throw new IllegalStateException("La cuenta ya está activa");
        }

        sendVerificationEmail(user);
        log.info("Verification email resent to {}", email);
    }

    private void sendVerificationEmail(User user) {
        emailVerificationService.sendVerificationCode(user.getEmail());
    }

    // ─── Verificación por SMS (alternativa al email) ─────────────────────────
    // Twilio Verify gestiona generación, expiración, reintentos y rate-limit
    // del OTP en su lado; aquí solo se controla el estado de la cuenta.

    @Override
    public void sendPhoneVerification(String email) {
        User user = requirePendingEmailUser(email);
        twilioSmsService.sendOtp(user.getPhoneNumber());
        log.info("SMS verification code sent for account {}", email);
    }

    @Override
    public void verifyPhoneCode(String email, String code) {
        User user = requirePendingEmailUser(email);

        if (!twilioSmsService.verifyOtp(user.getPhoneNumber(), code)) {
            throw new IllegalArgumentException("Código de verificación incorrecto o expirado");
        }

        user.setUserState(UserState.ACTIVE);
        userRepository.save(user);
        log.info("Account {} activated via SMS verification", email);
    }

    private User requirePendingEmailUser(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("No existe una cuenta con ese correo"));
        if (user.getUserState() != UserState.PENDING_EMAIL) {
            throw new IllegalStateException("La cuenta ya está activa");
        }
        return user;
    }

    // ─── Recuperación de contraseña ───────────────────────────────────────────

    @Override
    public void requestPasswordReset(String email) {
        // Anti-enumeración: si el correo no existe, no hacer nada y responder igual
        userRepository.findByEmail(email).ifPresentOrElse(
                user -> emailVerificationService.sendPasswordResetCode(email),
                () -> log.info("Password reset requested for unknown email"));
    }

    @Override
    @Transactional
    public void resetPassword(String email, String code, String newPassword) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Código de verificación incorrecto"));

        emailVerificationService.verifyCode(email, code);

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setPasswordConfigured(true);
        userRepository.save(user);

        // Cerrar todas las sesiones activas: la contraseña cambió
        refreshTokenRepository.deleteByUsername(user);

        log.info("Password reset completed for user {}", email);
    }

    private String normalizeUsername(String u) {
        return u == null ? null : u.trim();
    }
    private int calculateAge(LocalDate birthDate) {
        return Period.between(birthDate, LocalDate.now()).getYears();
    }

    private void validateEmailAndPhoneNumber(String email, String phoneNumber) {

        if (userRepository.existsByEmail(email)) {
            throw new ValidationException("Email already registered");
        }

        if (userRepository.existsByPhoneNumber(phoneNumber)) {
            throw new ValidationException("Phone number already registered");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public User getUserById(Long id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("UserId cannot be null or minor than 1");
        }
        return userRepository.findById(id)
                .orElseThrow(() -> new ObjectNotFoundException("User not found for userId: " + id, User.class));
    }

    @Override
    @Transactional(readOnly = true)
    public User getUserByEmail(String email) {
        if (email == null || email.isBlank() || !email.contains("@")) {
            throw new IllegalArgumentException("Invalid email");
        }
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ObjectNotFoundException("User", User.class));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean emailExists(String email) {
        if (email == null || email.isBlank() || !email.contains("@")) {
            throw new IllegalArgumentException("Invalid email");
        }
        return userRepository.existsByEmail(email);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean phoneExists(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isBlank() || phoneNumber.length() != 10) {
            throw new IllegalArgumentException("Invalid phone number");
        }
        return userRepository.existsByPhoneNumber(phoneNumber);
    }

    @Override
    public void deleteById(Long id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("UserId cannot be null or minor than 1");
        }
        userRepository.deleteById(id);
    }
}
