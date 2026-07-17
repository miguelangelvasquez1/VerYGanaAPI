package com.verygana2.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.verygana2.exceptions.authExceptions.AccountLockedException;
import com.verygana2.models.User;
import com.verygana2.repositories.UserRepository;
import com.verygana2.security.auth.AccountLockService;
import com.verygana2.security.auth.refreshToken.SecurityAuditService;
import com.verygana2.services.interfaces.EmailVerificationService;

import jakarta.persistence.EntityNotFoundException;

@ExtendWith(MockitoExtension.class)
@DisplayName("AccountLockService")
class AccountLockServiceTest {

    @Mock UserRepository userRepository;
    @Mock EmailVerificationService emailVerificationService;
    @Mock SecurityAuditService securityAuditService;

    @InjectMocks AccountLockService service;

    private User user;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "maxFailedAttempts", 3);

        user = new User();
        user.setId(1L);
        user.setEmail("user@test.com");
        user.setFailedLoginAttempts(0);
    }

    @Nested
    @DisplayName("registerFailedAttempt")
    class RegisterFailedAttempt {

        @Test
        @DisplayName("incrementa el contador sin bloquear si no cruza el umbral")
        void incrementsWithoutLocking() {
            when(userRepository.findByEmailOrPhoneNumber("user@test.com", "user@test.com"))
                    .thenReturn(Optional.of(user));

            service.registerFailedAttempt("user@test.com");

            assertThat(user.getFailedLoginAttempts()).isEqualTo(1);
            assertThat(user.getAccountLockedAt()).isNull();
            verify(emailVerificationService, never()).sendVerificationCode(anyString());
        }

        @Test
        @DisplayName("bloquea la cuenta y envía el código al cruzar el umbral")
        void locksAndSendsCodeAtThreshold() {
            user.setFailedLoginAttempts(2);
            when(userRepository.findByEmailOrPhoneNumber("user@test.com", "user@test.com"))
                    .thenReturn(Optional.of(user));

            service.registerFailedAttempt("user@test.com");

            assertThat(user.getAccountLockedAt()).isNotNull();
            assertThat(user.getFailedLoginAttempts()).isZero();
            verify(emailVerificationService).sendVerificationCode("user@test.com");
            verify(securityAuditService).logSuspiciousActivity(
                    eq("user@test.com"), eq("ACCOUNT_LOCKED_FAILED_ATTEMPTS"), anyString());
        }

        @Test
        @DisplayName("no hace nada si el identificador no existe")
        void doesNothingWhenUserNotFound() {
            when(userRepository.findByEmailOrPhoneNumber(anyString(), anyString())).thenReturn(Optional.empty());

            service.registerFailedAttempt("ghost@test.com");

            verify(userRepository, never()).save(any());
            verify(emailVerificationService, never()).sendVerificationCode(anyString());
        }
    }

    @Nested
    @DisplayName("isLocked")
    class IsLocked {

        @Test
        @DisplayName("true cuando accountLockedAt está seteado")
        void trueWhenLocked() {
            user.setAccountLockedAt(java.time.Instant.now());
            when(userRepository.findByEmailOrPhoneNumber("user@test.com", "user@test.com"))
                    .thenReturn(Optional.of(user));

            assertThat(service.isLocked("user@test.com")).isTrue();
        }

        @Test
        @DisplayName("false cuando el usuario no existe")
        void falseWhenUserNotFound() {
            when(userRepository.findByEmailOrPhoneNumber(anyString(), anyString())).thenReturn(Optional.empty());

            assertThat(service.isLocked("ghost@test.com")).isFalse();
        }
    }

    @Nested
    @DisplayName("unlock")
    class Unlock {

        @Test
        @DisplayName("limpia el bloqueo y el contador cuando el código es correcto")
        void clearsLockOnValidCode() {
            user.setAccountLockedAt(java.time.Instant.now());
            user.setFailedLoginAttempts(0);
            when(userRepository.findByEmailOrPhoneNumber("user@test.com", "user@test.com"))
                    .thenReturn(Optional.of(user));

            service.unlock("user@test.com", "123456");

            verify(emailVerificationService).verifyCode("user@test.com", "123456");
            assertThat(user.getAccountLockedAt()).isNull();
            assertThat(user.getFailedLoginAttempts()).isZero();
        }

        @Test
        @DisplayName("lanza AccountLockedException si la cuenta no está bloqueada")
        void throwsWhenNotLocked() {
            when(userRepository.findByEmailOrPhoneNumber("user@test.com", "user@test.com"))
                    .thenReturn(Optional.of(user));

            assertThatThrownBy(() -> service.unlock("user@test.com", "123456"))
                    .isInstanceOf(AccountLockedException.class);

            verify(emailVerificationService, never()).verifyCode(anyString(), anyString());
        }

        @Test
        @DisplayName("lanza EntityNotFoundException si la cuenta no existe")
        void throwsWhenUserNotFound() {
            when(userRepository.findByEmailOrPhoneNumber(anyString(), anyString())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.unlock("ghost@test.com", "123456"))
                    .isInstanceOf(EntityNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("registerSuccessfulLogin")
    class RegisterSuccessfulLogin {

        @Test
        @DisplayName("resetea el contador si tenía intentos fallidos")
        void resetsCounter() {
            user.setFailedLoginAttempts(2);
            when(userRepository.findByEmailOrPhoneNumber("user@test.com", "user@test.com"))
                    .thenReturn(Optional.of(user));

            service.registerSuccessfulLogin("user@test.com");

            assertThat(user.getFailedLoginAttempts()).isZero();
            verify(userRepository, times(1)).save(user);
        }

        @Test
        @DisplayName("no guarda si el contador ya estaba en cero")
        void doesNotSaveWhenAlreadyZero() {
            when(userRepository.findByEmailOrPhoneNumber("user@test.com", "user@test.com"))
                    .thenReturn(Optional.of(user));

            service.registerSuccessfulLogin("user@test.com");

            verify(userRepository, never()).save(any());
        }
    }
}
