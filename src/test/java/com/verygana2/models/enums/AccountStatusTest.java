package com.verygana2.models.enums;

import static org.assertj.core.api.Assertions.assertThat;
import static com.verygana2.models.enums.AccountStatus.*;

import java.util.EnumSet;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("AccountStatus.canTransitionTo")
class AccountStatusTest {

    @Nested
    @DisplayName("REGISTRATION_STARTED")
    class RegistrationStarted {
        @Test
        @DisplayName("permite PENDING_ACCEPTANCE, SUSPENDED y TERMINATED")
        void allowed() {
            assertAllowed(REGISTRATION_STARTED, PENDING_ACCEPTANCE, SUSPENDED, TERMINATED);
        }

        @Test
        @DisplayName("no permite saltar directo a ACTIVE ni a PENDING_VERIFICATION/PENDING_ACTIVATION")
        void disallowed() {
            assertDisallowed(REGISTRATION_STARTED, PENDING_VERIFICATION, PENDING_ACTIVATION, ACTIVE, PREVENTIVELY_RESTRICTED);
        }
    }

    @Nested
    @DisplayName("PENDING_ACCEPTANCE")
    class PendingAcceptance {
        @Test
        @DisplayName("permite PENDING_VERIFICATION, SUSPENDED y TERMINATED")
        void allowed() {
            assertAllowed(PENDING_ACCEPTANCE, PENDING_VERIFICATION, SUSPENDED, TERMINATED);
        }

        @Test
        @DisplayName("no permite saltar directo a ACTIVE")
        void disallowed() {
            assertDisallowed(PENDING_ACCEPTANCE, REGISTRATION_STARTED, PENDING_ACTIVATION, ACTIVE, PREVENTIVELY_RESTRICTED);
        }
    }

    @Nested
    @DisplayName("PENDING_VERIFICATION")
    class PendingVerification {
        @Test
        @DisplayName("permite PENDING_ACTIVATION, ACTIVE, SUSPENDED y TERMINATED")
        void allowed() {
            assertAllowed(PENDING_VERIFICATION, PENDING_ACTIVATION, ACTIVE, SUSPENDED, TERMINATED);
        }

        @Test
        @DisplayName("no permite volver a estados previos de registro")
        void disallowed() {
            assertDisallowed(PENDING_VERIFICATION, REGISTRATION_STARTED, PENDING_ACCEPTANCE, PREVENTIVELY_RESTRICTED);
        }
    }

    @Nested
    @DisplayName("PENDING_ACTIVATION")
    class PendingActivation {
        @Test
        @DisplayName("permite ACTIVE, SUSPENDED y TERMINATED")
        void allowed() {
            assertAllowed(PENDING_ACTIVATION, ACTIVE, SUSPENDED, TERMINATED);
        }

        @Test
        @DisplayName("no permite volver a estados previos de registro ni PREVENTIVELY_RESTRICTED")
        void disallowed() {
            assertDisallowed(PENDING_ACTIVATION, REGISTRATION_STARTED, PENDING_ACCEPTANCE, PENDING_VERIFICATION, PREVENTIVELY_RESTRICTED);
        }
    }

    @Nested
    @DisplayName("ACTIVE")
    class Active {
        @Test
        @DisplayName("permite PREVENTIVELY_RESTRICTED, SUSPENDED y TERMINATED")
        void allowed() {
            assertAllowed(ACTIVE, PREVENTIVELY_RESTRICTED, SUSPENDED, TERMINATED);
        }

        @Test
        @DisplayName("no permite volver a ningún estado de registro/pendiente")
        void disallowed() {
            assertDisallowed(ACTIVE, REGISTRATION_STARTED, PENDING_ACCEPTANCE, PENDING_VERIFICATION, PENDING_ACTIVATION);
        }
    }

    @Nested
    @DisplayName("PREVENTIVELY_RESTRICTED")
    class PreventivelyRestricted {
        @Test
        @DisplayName("permite ACTIVE, SUSPENDED y TERMINATED")
        void allowed() {
            assertAllowed(PREVENTIVELY_RESTRICTED, ACTIVE, SUSPENDED, TERMINATED);
        }

        @Test
        @DisplayName("no permite volver a ningún estado de registro/pendiente")
        void disallowed() {
            assertDisallowed(PREVENTIVELY_RESTRICTED, REGISTRATION_STARTED, PENDING_ACCEPTANCE, PENDING_VERIFICATION, PENDING_ACTIVATION);
        }
    }

    @Nested
    @DisplayName("SUSPENDED")
    class Suspended {
        @Test
        @DisplayName("permite volver a cualquier estado no final del que pudo venir (según lo que diga el historial) y a TERMINATED")
        void allowed() {
            assertAllowed(SUSPENDED, REGISTRATION_STARTED, PENDING_ACCEPTANCE, PENDING_VERIFICATION,
                    PENDING_ACTIVATION, ACTIVE, PREVENTIVELY_RESTRICTED, TERMINATED);
        }
    }

    @Nested
    @DisplayName("TERMINATED")
    class Terminated {
        @Test
        @DisplayName("es un estado final: no permite ninguna transición de salida")
        void noOutgoingTransitions() {
            for (AccountStatus target : AccountStatus.values()) {
                assertThat(TERMINATED.canTransitionTo(target))
                        .as("TERMINATED -> %s debería estar prohibido", target)
                        .isFalse();
            }
        }
    }

    @Test
    @DisplayName("ningún estado permite transicionar a sí mismo")
    void noSelfTransitions() {
        for (AccountStatus status : AccountStatus.values()) {
            assertThat(status.canTransitionTo(status))
                    .as("%s -> %s (mismo estado) debería estar prohibido", status, status)
                    .isFalse();
        }
    }

    private void assertAllowed(AccountStatus from, AccountStatus... targets) {
        for (AccountStatus target : targets) {
            assertThat(from.canTransitionTo(target))
                    .as("%s -> %s debería estar permitido", from, target)
                    .isTrue();
        }
    }

    private void assertDisallowed(AccountStatus from, AccountStatus... targets) {
        for (AccountStatus target : targets) {
            assertThat(from.canTransitionTo(target))
                    .as("%s -> %s debería estar prohibido", from, target)
                    .isFalse();
        }
    }
}
