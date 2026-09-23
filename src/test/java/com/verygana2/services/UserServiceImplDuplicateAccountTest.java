package com.verygana2.services;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.util.Collections;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.verygana2.dtos.user.ConsumerRegisterDTO;
import com.verygana2.mappers.UserMapper;
import com.verygana2.repositories.PhoneNumberHistoryRepository;
import com.verygana2.repositories.UserRepository;
import com.verygana2.repositories.details.ConsumerDetailsRepository;
import com.verygana2.security.auth.refreshToken.RefreshTokenRepository;
import com.verygana2.services.interfaces.AccountStatusService;
import com.verygana2.services.interfaces.AvatarService;
import com.verygana2.services.interfaces.EmailVerificationService;
import com.verygana2.services.interfaces.OutboxService;
import com.verygana2.services.interfaces.PasswordSetupService;
import com.verygana2.services.interfaces.ReferralService;
import com.verygana2.services.interfaces.TwilioSmsService;
import com.verygana2.services.interfaces.UserService;
import com.verygana2.services.interfaces.eligibility.ConsumerEligibilityService;
import com.verygana2.services.interfaces.finance.KeyWalletService;
import com.verygana2.services.interfaces.compliance.ScreeningService;
import com.verygana2.services.interfaces.levels.LevelService;
import com.verygana2.utils.generators.UserHashGenerator;
import com.verygana2.services.LocationService;

@ExtendWith(MockitoExtension.class)
class UserServiceImplDuplicateAccountTest {

    @Mock ReferralService referralService;
    @Mock AvatarService avatarService;
    @Mock UserHashGenerator userHashGenerator;
    @Mock UserRepository userRepository;
    @Mock KeyWalletService keyWalletService;
    @Mock PasswordEncoder passwordEncoder;
    @Mock UserMapper userMapper;
    @Mock OutboxService outboxService;
    @Mock LocationService locationService;
    @Mock LevelService levelService;
    @Mock PasswordSetupService passwordSetupService;
    @Mock EmailVerificationService emailVerificationService;
    @Mock ScreeningService screeningService;
    @Mock TwilioSmsService twilioSmsService;
    @Mock RefreshTokenRepository refreshTokenRepository;
    @Mock ApplicationEventPublisher eventPublisher;
    @Mock AccountStatusService accountStatusService;
    @Mock ConsumerEligibilityService consumerEligibilityService;
    @Mock PhoneNumberHistoryRepository phoneNumberHistoryRepository;
    @Mock ConsumerDetailsRepository consumerDetailsRepository;
    @Mock Clock clock;

    @InjectMocks
    UserServiceImpl service;

    @Test
    void registerConsumer_DuplicateDocumentIsPossibleDuplicityAndDoesNotSave() {
        ConsumerRegisterDTO dto = new ConsumerRegisterDTO();
        dto.setDocumentNumber("123456789");
        dto.setEmail("new@example.com");

        when(consumerDetailsRepository.existsByDocumentNumber(dto.getDocumentNumber())).thenReturn(true);

        assertThatThrownBy(() -> service.registerConsumer(dto))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("documento");

        verify(userRepository, never()).save(org.mockito.ArgumentMatchers.any());
        verify(userRepository, never()).saveAndFlush(org.mockito.ArgumentMatchers.any());
    }
}
