package com.verygana2.services.finance;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.verygana2.models.finance.KeyTransaction;
import com.verygana2.models.finance.KeyWallet;
import com.verygana2.repositories.finance.KeyTransactionRepository;
import com.verygana2.repositories.finance.KeyWalletRepository;
import com.verygana2.services.interfaces.finance.TreasuryService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests de {@link KeyExpiryServiceImpl}: el job nocturno que debita las
 * llaves vencidas. Agrupa por wallet, nunca expira más de lo disponible
 * (respeta lo bloqueado), y mueve el valor total a FORTIFICATION solo si
 * algo se expiró de verdad.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("KeyExpiryServiceImpl")
class KeyExpiryServiceImplTest {

    @Mock private KeyTransactionRepository keyTransactionRepository;
    @Mock private KeyWalletRepository keyWalletRepository;
    @Mock private TreasuryService treasuryService;

    private KeyExpiryServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new KeyExpiryServiceImpl(keyTransactionRepository, keyWalletRepository, treasuryService);
    }

    private KeyTransaction expiredCredit(KeyWallet wallet, long purchaseDeltaCents, long connectivityDeltaCents) {
        return KeyTransaction.builder()
                .id(UUID.randomUUID())
                .keyWallet(wallet)
                .purchaseKeysDeltaCents(purchaseDeltaCents > 0 ? purchaseDeltaCents : null)
                .connectivityKeysDeltaCents(connectivityDeltaCents > 0 ? connectivityDeltaCents : null)
                .build();
    }

    @Test
    @DisplayName("sin créditos vencidos: no hace nada, no mueve tesorería")
    void noExpiredCredits_doesNothing() {
        when(keyTransactionRepository.findExpiredNotProcessed(any())).thenReturn(List.of());

        service.processExpiredKeys();

        verify(keyWalletRepository, never()).save(any());
        verify(treasuryService, never()).moveExpiredKeysToFortification(any(), any());
    }

    @Test
    @DisplayName("expira lo disponible del wallet y mueve el valor total a FORTIFICATION")
    void expiresAvailableKeysAndMovesToFortification() {
        KeyWallet wallet = KeyWallet.builder().purchaseKeysCents(50_000L).connectivityKeysCents(20_000L).build();
        KeyTransaction credit1 = expiredCredit(wallet, 30_000L, 0L);
        KeyTransaction credit2 = expiredCredit(wallet, 0L, 10_000L);

        when(keyTransactionRepository.findExpiredNotProcessed(any())).thenReturn(List.of(credit1, credit2));

        service.processExpiredKeys();

        assertThat(wallet.getPurchaseKeysCents()).isEqualTo(20_000L); // 50 - 30
        assertThat(wallet.getConnectivityKeysCents()).isEqualTo(10_000L); // 20 - 10
        verify(keyWalletRepository).save(wallet);
        verify(keyTransactionRepository).save(any(KeyTransaction.class));
        // 30.000 + 10.000 centavos = 40.000
        verify(treasuryService).moveExpiredKeysToFortification(eq(40_000L), any());
    }

    @Test
    @DisplayName("nunca expira más de lo que hay disponible en el wallet (respeta lo ya gastado)")
    void neverExpiresMoreThanAvailable() {
        // El wallet ya gastó la mayoría de sus llaves; el crédito vencido dice 100 pero solo quedan 5.
        KeyWallet wallet = KeyWallet.builder().purchaseKeysCents(5_000L).connectivityKeysCents(0L).build();
        KeyTransaction credit = expiredCredit(wallet, 100_000L, 0L);

        when(keyTransactionRepository.findExpiredNotProcessed(any())).thenReturn(List.of(credit));

        service.processExpiredKeys();

        assertThat(wallet.getPurchaseKeysCents()).isZero(); // solo expiró lo disponible (5.000), no los 100.000 nominales
        verify(treasuryService).moveExpiredKeysToFortification(eq(5_000L), any());
    }

    @Test
    @DisplayName("wallet sin saldo disponible para expirar: se marca como procesado sin tocar tesorería para ese wallet")
    void walletWithNothingAvailable_marksProcessedWithoutTreasuryMove() {
        KeyWallet wallet = KeyWallet.builder().purchaseKeysCents(0L).connectivityKeysCents(0L).build();
        KeyTransaction credit = expiredCredit(wallet, 50_000L, 0L); // ya no queda nada disponible

        when(keyTransactionRepository.findExpiredNotProcessed(any())).thenReturn(List.of(credit));

        service.processExpiredKeys();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<UUID>> captor = ArgumentCaptor.forClass(List.class);
        verify(keyTransactionRepository).markAllAsProcessed(captor.capture());
        assertThat(captor.getValue()).containsExactly(credit.getId());
        verify(keyWalletRepository, never()).save(any());
        verify(treasuryService, never()).moveExpiredKeysToFortification(any(), any());
    }
}
