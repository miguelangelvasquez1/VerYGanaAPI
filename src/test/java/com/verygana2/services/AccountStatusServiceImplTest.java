package com.verygana2.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.verygana2.exceptions.InvalidStatusException;
import com.verygana2.models.AccountStatusHistory;
import com.verygana2.models.User;
import com.verygana2.models.enums.AccountStatus;
import com.verygana2.repositories.AccountStatusHistoryRepository;
import com.verygana2.repositories.UserRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("AccountStatusServiceImpl.transition")
class AccountStatusServiceImplTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-01-15T10:00:00Z");

    @Mock UserRepository userRepository;
    @Mock AccountStatusHistoryRepository accountStatusHistoryRepository;

    private AccountStatusServiceImpl service;

    private User userWith(AccountStatus status) {
        User user = new User();
        user.setId(42L);
        user.setAccountStatus(status);
        return user;
    }

    private void withClock() {
        Clock clock = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);
        service = new AccountStatusServiceImpl(userRepository, accountStatusHistoryRepository, clock);
    }

    @Test
    @DisplayName("transición válida: guarda el nuevo estado en el user y una fila en el historial")
    void validTransitionSavesUserAndHistory() {
        withClock();
        User user = userWith(AccountStatus.PENDING_VERIFICATION);
        when(userRepository.findById(42L)).thenReturn(Optional.of(user));

        service.transition(42L, AccountStatus.ACTIVE, "Email verificado", "SYSTEM");

        assertThat(user.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
        verify(userRepository).save(user);

        ArgumentCaptor<AccountStatusHistory> captor = ArgumentCaptor.forClass(AccountStatusHistory.class);
        verify(accountStatusHistoryRepository).save(captor.capture());
        AccountStatusHistory history = captor.getValue();
        assertThat(history.getUserId()).isEqualTo(42L);
        assertThat(history.getFromStatus()).isEqualTo(AccountStatus.PENDING_VERIFICATION);
        assertThat(history.getToStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(history.getReason()).isEqualTo("Email verificado");
        assertThat(history.getActor()).isEqualTo("SYSTEM");
        assertThat(history.getOccurredAt()).isEqualTo(ZonedDateTime.now(Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC)));
    }

    @Test
    @DisplayName("transición inválida: lanza InvalidStatusException y no guarda nada")
    void invalidTransitionThrowsAndSavesNothing() {
        withClock();
        User user = userWith(AccountStatus.TERMINATED);
        when(userRepository.findById(42L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.transition(42L, AccountStatus.ACTIVE, "reintento", "admin@verygana.com"))
                .isInstanceOf(InvalidStatusException.class);

        assertThat(user.getAccountStatus()).isEqualTo(AccountStatus.TERMINATED);
        verify(userRepository, never()).save(any());
        verify(accountStatusHistoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("transición al mismo estado: lanza InvalidStatusException (no hay auto-transiciones)")
    void selfTransitionThrows() {
        withClock();
        User user = userWith(AccountStatus.ACTIVE);
        when(userRepository.findById(42L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.transition(42L, AccountStatus.ACTIVE, "no-op", "SYSTEM"))
                .isInstanceOf(InvalidStatusException.class);

        verify(userRepository, never()).save(any());
        verify(accountStatusHistoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("usuario inexistente: propaga la excepción sin intentar guardar")
    void userNotFoundThrows() {
        withClock();
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.transition(999L, AccountStatus.ACTIVE, "x", "SYSTEM"))
                .isInstanceOf(RuntimeException.class);

        verify(accountStatusHistoryRepository, never()).save(any());
        verify(userRepository, times(1)).findById(999L);
    }
}
