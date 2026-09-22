package com.verygana2.services.details;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.verygana2.exceptions.InvalidRequestException;
import com.verygana2.mappers.UserMapper;
import com.verygana2.models.AccountStatusHistory;
import com.verygana2.models.User;
import com.verygana2.models.enums.AccountStatus;
import com.verygana2.models.userDetails.UserDetails;
import com.verygana2.repositories.AccountStatusHistoryRepository;
import com.verygana2.repositories.UserRepository;
import com.verygana2.repositories.details.UserDetailsRepository;
import com.verygana2.services.interfaces.AccountStatusService;
import com.verygana2.services.interfaces.NotificationService;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserDetailsServiceImpl block/unblock")
class UserDetailsServiceImplTest {

    private static final UUID PUBLIC_ID = UUID.randomUUID();
    private static final Long USER_ID = 7L;

    @Mock UserDetailsRepository userDetailsRepository;
    @Mock UserRepository userRepository;
    @Mock NotificationService notificationService;
    @Mock UserMapper userMapper;
    @Mock AccountStatusService accountStatusService;
    @Mock AccountStatusHistoryRepository accountStatusHistoryRepository;

    @InjectMocks
    UserDetailsServiceImpl service;

    private UserDetails userDetailsWith(AccountStatus status) {
        User user = new User();
        user.setId(USER_ID);
        user.setAccountStatus(status);

        UserDetails userDetails = org.mockito.Mockito.mock(UserDetails.class);
        when(userDetails.getUser()).thenReturn(user);
        return userDetails;
    }

    @Nested
    @DisplayName("blockUser")
    class BlockUser {

        @Test
        @DisplayName("cuenta ACTIVE: transiciona a SUSPENDED con el reason y actor dados")
        void blocksActiveAccount() {
            UserDetails userDetails = userDetailsWith(AccountStatus.ACTIVE);
            when(userDetails.getId()).thenReturn(USER_ID);
            when(userDetailsRepository.findByPublicId(PUBLIC_ID)).thenReturn(Optional.of(userDetails));

            service.blockUser(PUBLIC_ID, "Actividad sospechosa", "admin@verygana.com");

            verify(accountStatusService).transition(USER_ID, AccountStatus.SUSPENDED, "Actividad sospechosa", "admin@verygana.com");
            verify(notificationService).createInternalNotification(eq(USER_ID), any(), eq("Actividad sospechosa"), any());
        }

        @Test
        @DisplayName("cuenta ya SUSPENDED: lanza InvalidRequestException sin llamar a transition")
        void alreadySuspendedThrows() {
            UserDetails userDetails = userDetailsWith(AccountStatus.SUSPENDED);
            when(userDetailsRepository.findByPublicId(PUBLIC_ID)).thenReturn(Optional.of(userDetails));

            assertThatThrownBy(() -> service.blockUser(PUBLIC_ID, "de nuevo", "admin@verygana.com"))
                    .isInstanceOf(InvalidRequestException.class);

            verify(accountStatusService, never()).transition(any(), any(), any(), any());
        }
    }

    @Nested
    @DisplayName("unblockUser")
    class UnblockUser {

        @Test
        @DisplayName("con historial: vuelve al estado previo a la última suspensión, no a ACTIVE")
        void restoresStatusBeforeLastSuspension() {
            UserDetails userDetails = userDetailsWith(AccountStatus.SUSPENDED);
            when(userDetails.getId()).thenReturn(USER_ID);
            when(userDetailsRepository.findByPublicId(PUBLIC_ID)).thenReturn(Optional.of(userDetails));

            AccountStatusHistory lastSuspension = AccountStatusHistory.builder()
                    .userId(USER_ID)
                    .fromStatus(AccountStatus.PENDING_ACTIVATION)
                    .toStatus(AccountStatus.SUSPENDED)
                    .reason("KYC rechazado")
                    .actor("compliance@verygana.com")
                    .build();
            when(accountStatusHistoryRepository.findFirstByUserIdAndToStatusOrderByOccurredAtDesc(USER_ID, AccountStatus.SUSPENDED))
                    .thenReturn(Optional.of(lastSuspension));

            service.unblockUser(PUBLIC_ID, "Revisión manual favorable", "admin@verygana.com");

            verify(accountStatusService).transition(USER_ID, AccountStatus.PENDING_ACTIVATION, "Revisión manual favorable", "admin@verygana.com");
        }

        @Test
        @DisplayName("suspendido desde PREVENTIVELY_RESTRICTED: al desbloquear vuelve a PREVENTIVELY_RESTRICTED, no a ACTIVE")
        void restoresPreventivelyRestrictedBeforeLastSuspension() {
            UserDetails userDetails = userDetailsWith(AccountStatus.SUSPENDED);
            when(userDetails.getId()).thenReturn(USER_ID);
            when(userDetailsRepository.findByPublicId(PUBLIC_ID)).thenReturn(Optional.of(userDetails));

            AccountStatusHistory lastSuspension = AccountStatusHistory.builder()
                    .userId(USER_ID)
                    .fromStatus(AccountStatus.PREVENTIVELY_RESTRICTED)
                    .toStatus(AccountStatus.SUSPENDED)
                    .reason("Señal de duplicado confirmada como fraude")
                    .actor("compliance@verygana.com")
                    .build();
            when(accountStatusHistoryRepository.findFirstByUserIdAndToStatusOrderByOccurredAtDesc(USER_ID, AccountStatus.SUSPENDED))
                    .thenReturn(Optional.of(lastSuspension));

            service.unblockUser(PUBLIC_ID, "Revisión manual favorable", "admin@verygana.com");

            verify(accountStatusService).transition(USER_ID, AccountStatus.PREVENTIVELY_RESTRICTED, "Revisión manual favorable", "admin@verygana.com");
        }

        @Test
        @DisplayName("sin historial (suspendido antes de este cambio): vuelve a ACTIVE")
        void fallsBackToActiveWithoutHistory() {
            UserDetails userDetails = userDetailsWith(AccountStatus.SUSPENDED);
            when(userDetails.getId()).thenReturn(USER_ID);
            when(userDetailsRepository.findByPublicId(PUBLIC_ID)).thenReturn(Optional.of(userDetails));
            when(accountStatusHistoryRepository.findFirstByUserIdAndToStatusOrderByOccurredAtDesc(USER_ID, AccountStatus.SUSPENDED))
                    .thenReturn(Optional.empty());

            service.unblockUser(PUBLIC_ID, "Revisión manual favorable", "admin@verygana.com");

            verify(accountStatusService).transition(USER_ID, AccountStatus.ACTIVE, "Revisión manual favorable", "admin@verygana.com");
        }

        @Test
        @DisplayName("cuenta no SUSPENDED (ej. TERMINATED): lanza InvalidRequestException sin llamar a transition, no puede reactivarse")
        void nonSuspendedAccountCannotBeUnblocked() {
            UserDetails userDetails = userDetailsWith(AccountStatus.TERMINATED);
            when(userDetailsRepository.findByPublicId(PUBLIC_ID)).thenReturn(Optional.of(userDetails));

            assertThatThrownBy(() -> service.unblockUser(PUBLIC_ID, "intento", "admin@verygana.com"))
                    .isInstanceOf(InvalidRequestException.class);

            verify(accountStatusService, never()).transition(any(), any(), any(), any());
            verify(accountStatusHistoryRepository, never()).findFirstByUserIdAndToStatusOrderByOccurredAtDesc(any(), any());
        }
    }
}
