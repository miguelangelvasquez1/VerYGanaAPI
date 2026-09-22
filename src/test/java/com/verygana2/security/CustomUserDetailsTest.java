package com.verygana2.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.AuthorityUtils;

import com.verygana2.models.User;
import com.verygana2.models.enums.AccountStatus;

/**
 * Caracterización del gating de login por estado de cuenta.
 * Mismas aserciones que antes del rename UserState -> AccountStatus; solo
 * cambian los valores del enum usados aquí.
 */
@DisplayName("CustomUserDetails")
class CustomUserDetailsTest {

    private CustomUserDetails userWith(AccountStatus status, boolean passwordConfigured) {
        User user = new User();
        user.setId(1L);
        user.setEmail("consumer@test.com");
        user.setPassword("{bcrypt}irrelevante");
        user.setPasswordConfigured(passwordConfigured);
        user.setAccountStatus(status);
        return new CustomUserDetails(user, AuthorityUtils.createAuthorityList("ROLE_CONSUMER"));
    }

    @Nested
    @DisplayName("ACTIVE")
    class Active {

        @Test
        @DisplayName("con password configurada: habilitado y no bloqueado")
        void enabledAndUnlocked() {
            CustomUserDetails details = userWith(AccountStatus.ACTIVE, true);

            assertThat(details.isEnabled()).isTrue();
            assertThat(details.isAccountNonLocked()).isTrue();
        }

        @Test
        @DisplayName("sin password configurada: deshabilitado pero no bloqueado")
        void disabledWhenPasswordNotConfigured() {
            CustomUserDetails details = userWith(AccountStatus.ACTIVE, false);

            assertThat(details.isEnabled()).isFalse();
            assertThat(details.isAccountNonLocked()).isTrue();
        }
    }

    @Nested
    @DisplayName("PENDING_VERIFICATION")
    class PendingVerification {

        @Test
        @DisplayName("deshabilitado y no bloqueado")
        void disabledNotLocked() {
            CustomUserDetails details = userWith(AccountStatus.PENDING_VERIFICATION, true);

            assertThat(details.isEnabled()).isFalse();
            assertThat(details.isAccountNonLocked()).isTrue();
        }
    }

    @Nested
    @DisplayName("PENDING_ACTIVATION")
    class PendingActivation {

        @Test
        @DisplayName("deshabilitado y no bloqueado")
        void disabledNotLocked() {
            CustomUserDetails details = userWith(AccountStatus.PENDING_ACTIVATION, true);

            assertThat(details.isEnabled()).isFalse();
            assertThat(details.isAccountNonLocked()).isTrue();
        }
    }

    @Nested
    @DisplayName("SUSPENDED")
    class Suspended {

        @Test
        @DisplayName("bloqueado, independientemente de la password")
        void locked() {
            CustomUserDetails details = userWith(AccountStatus.SUSPENDED, true);

            assertThat(details.isAccountNonLocked()).isFalse();
        }
    }

    @Nested
    @DisplayName("Estados nuevos sin caso de login definido")
    class NewStatesDenyLoginByDefault {

        @Test
        @DisplayName("REGISTRATION_STARTED: deshabilitado y no bloqueado (denegado por isEnabled)")
        void registrationStarted() {
            CustomUserDetails details = userWith(AccountStatus.REGISTRATION_STARTED, true);

            assertThat(details.isEnabled()).isFalse();
            assertThat(details.isAccountNonLocked()).isTrue();
        }

        @Test
        @DisplayName("PENDING_ACCEPTANCE: deshabilitado y no bloqueado (denegado por isEnabled)")
        void pendingAcceptance() {
            CustomUserDetails details = userWith(AccountStatus.PENDING_ACCEPTANCE, true);

            assertThat(details.isEnabled()).isFalse();
            assertThat(details.isAccountNonLocked()).isTrue();
        }

        @Test
        @DisplayName("PREVENTIVELY_RESTRICTED: bloqueado")
        void preventivelyRestricted() {
            CustomUserDetails details = userWith(AccountStatus.PREVENTIVELY_RESTRICTED, true);

            assertThat(details.isAccountNonLocked()).isFalse();
        }

        @Test
        @DisplayName("TERMINATED: bloqueado")
        void terminated() {
            CustomUserDetails details = userWith(AccountStatus.TERMINATED, true);

            assertThat(details.isAccountNonLocked()).isFalse();
        }
    }

    @Test
    @DisplayName("isAccountNonExpired e isCredentialsNonExpired siempre son true")
    void neverExpires() {
        CustomUserDetails details = userWith(AccountStatus.ACTIVE, true);

        assertThat(details.isAccountNonExpired()).isTrue();
        assertThat(details.isCredentialsNonExpired()).isTrue();
    }
}
