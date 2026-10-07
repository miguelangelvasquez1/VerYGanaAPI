package com.verygana2.services.finance;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.verygana2.repositories.finance.KeyLotRepository;
import com.verygana2.services.interfaces.finance.KeyLedgerService;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests de {@link KeyExpiryServiceImpl}: el job nocturno que recorre las billeteras con
 * lotes vencidos. Lo que vence en cada una, y cuánto, lo cubre
 * KeyLedgerServiceIntegrationTest; aquí solo importa que cada billetera se procese por
 * separado y que el fallo de una no detenga a las demás.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("KeyExpiryServiceImpl")
class KeyExpiryServiceImplTest {

    @Mock private KeyLotRepository keyLotRepository;
    @Mock private KeyLedgerService keyLedgerService;

    private KeyExpiryServiceImpl service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-11-01T10:05:00Z"), ZoneOffset.UTC);
        service = new KeyExpiryServiceImpl(keyLotRepository, keyLedgerService, clock);
    }

    @Test
    @DisplayName("sin lotes vencidos: no toca ninguna billetera")
    void noDueLots_doesNothing() {
        when(keyLotRepository.findWalletIdsWithDueLots(any())).thenReturn(List.of());

        service.processExpiredKeys();

        verify(keyLedgerService, never()).expireDueLots(any(), any());
    }

    @Test
    @DisplayName("vence cada billetera con lotes vencidos, una por una")
    void expiresEachWalletWithDueLots() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        when(keyLotRepository.findWalletIdsWithDueLots(any())).thenReturn(List.of(first, second));

        service.processExpiredKeys();

        verify(keyLedgerService).expireDueLots(eq(first), any());
        verify(keyLedgerService).expireDueLots(eq(second), any());
    }

    @Test
    @DisplayName("si una billetera falla, las demás se procesan igual y el job no revienta")
    void failureInOneWallet_doesNotStopTheOthers() {
        UUID broken = UUID.randomUUID();
        UUID healthy = UUID.randomUUID();
        when(keyLotRepository.findWalletIdsWithDueLots(any())).thenReturn(List.of(broken, healthy));
        when(keyLedgerService.expireDueLots(eq(broken), any())).thenThrow(new IllegalStateException("boom"));

        assertThatCode(() -> service.processExpiredKeys()).doesNotThrowAnyException();

        verify(keyLedgerService).expireDueLots(eq(healthy), any());
    }
}
