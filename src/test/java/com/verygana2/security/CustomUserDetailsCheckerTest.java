package com.verygana2.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.authority.AuthorityUtils;

import com.verygana2.exceptions.authExceptions.PasswordNotConfiguredException;
import com.verygana2.exceptions.authExceptions.PendingEmailVerificationException;
import com.verygana2.exceptions.authExceptions.PendingKycReviewException;
import com.verygana2.models.User;
import com.verygana2.models.enums.AccountStatus;

/**
 * Caracterización del CustomUserDetailsChecker (usado como
 * preAuthenticationChecks en el AuthenticationProvider).
 * Mismas aserciones que antes del rename UserState -> AccountStatus para los
 * estados que ya existían; se agregan casos para los estados nuevos que
 * deben denegar login por defecto.
 */
@DisplayName("CustomUserDetailsChecker")
class CustomUserDetailsCheckerTest {

    private final CustomUserDetailsChecker checker = new CustomUserDetailsChecker();

    private CustomUserDetails userWith(AccountStatus status, boolean passwordConfigured) {
        User user = new User();
        user.setId(1L);
        user.setEmail("consumer@test.com");
        user.setPassword("{bcrypt}irrelevante");
        user.setPasswordConfigured(passwordConfigured);
        user.setAccountStatus(status);
        return new CustomUserDetails(user, AuthorityUtils.createAuthorityList("ROLE_CONSUMER"));
    }

    @Test
    @DisplayName("ACTIVE con password configurada: no lanza nada")
    void activePasses() {
        assertThatCode(() -> checker.check(userWith(AccountStatus.ACTIVE, true)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("ACTIVE sin password configurada: PasswordNotConfiguredException")
    void activeWithoutPasswordThrowsPasswordNotConfigured() {
        assertThatThrownBy(() -> checker.check(userWith(AccountStatus.ACTIVE, false)))
                .isInstanceOf(PasswordNotConfiguredException.class);
    }

    @Test
    @DisplayName("PENDING_VERIFICATION: PendingEmailVerificationException")
    void pendingVerificationThrowsPendingEmailVerification() {
        assertThatThrownBy(() -> checker.check(userWith(AccountStatus.PENDING_VERIFICATION, true)))
                .isInstanceOf(PendingEmailVerificationException.class);
    }

    @Test
    @DisplayName("PENDING_ACTIVATION: PendingKycReviewException")
    void pendingActivationThrowsPendingKycReview() {
        assertThatThrownBy(() -> checker.check(userWith(AccountStatus.PENDING_ACTIVATION, true)))
                .isInstanceOf(PendingKycReviewException.class);
    }

    @Test
    @DisplayName("SUSPENDED: LockedException, sin importar el estado de la password")
    void suspendedThrowsLockedBeforeCheckingEnabled() {
        assertThatThrownBy(() -> checker.check(userWith(AccountStatus.SUSPENDED, true)))
                .isInstanceOf(LockedException.class);

        assertThatThrownBy(() -> checker.check(userWith(AccountStatus.SUSPENDED, false)))
                .isInstanceOf(LockedException.class);
    }

    @Test
    @DisplayName("PREVENTIVELY_RESTRICTED: LockedException (estado nuevo, sin usuarios existentes)")
    void preventivelyRestrictedThrowsLocked() {
        assertThatThrownBy(() -> checker.check(userWith(AccountStatus.PREVENTIVELY_RESTRICTED, true)))
                .isInstanceOf(LockedException.class);
    }

    @Test
    @DisplayName("TERMINATED: LockedException (estado nuevo, sin usuarios existentes)")
    void terminatedThrowsLocked() {
        assertThatThrownBy(() -> checker.check(userWith(AccountStatus.TERMINATED, true)))
                .isInstanceOf(LockedException.class);
    }

    @Test
    @DisplayName("REGISTRATION_STARTED: DisabledException genérico (sin caso especial de login)")
    void registrationStartedThrowsGenericDisabled() {
        assertThatThrownBy(() -> checker.check(userWith(AccountStatus.REGISTRATION_STARTED, true)))
                .isExactlyInstanceOf(DisabledException.class);
    }

    @Test
    @DisplayName("PENDING_ACCEPTANCE: DisabledException genérico (sin caso especial de login)")
    void pendingAcceptanceThrowsGenericDisabled() {
        assertThatThrownBy(() -> checker.check(userWith(AccountStatus.PENDING_ACCEPTANCE, true)))
                .isExactlyInstanceOf(DisabledException.class);
    }
}
