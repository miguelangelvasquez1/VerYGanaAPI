package com.verygana2.services.finance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.verygana2.models.finance.KeyTransaction;
import com.verygana2.models.finance.KeyWallet;
import com.verygana2.models.userDetails.ConsumerDetails;
import com.verygana2.repositories.finance.KeyTransactionRepository;
import com.verygana2.repositories.finance.KeyWalletRepository;
import com.verygana2.services.interfaces.finance.TreasuryService;

@ExtendWith(MockitoExtension.class)
@DisplayName("KeyExpiryServiceImpl")
class KeyExpiryServiceImplTest {

    @Mock KeyTransactionRepository keyTransactionRepository;
    @Mock KeyWalletRepository keyWalletRepository;
    @Mock TreasuryService treasuryService;

    @InjectMocks KeyExpiryServiceImpl service;

    @BeforeEach
    void injectKeyValueCents() {
        // @Value is not processed by MockitoExtension — inject manually
        ReflectionTestUtils.setField(service, "keyValueCents", 1000L);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private KeyWallet walletWithBalance(long purchaseKeys, long connectivityKeys) {
        ConsumerDetails consumer = new ConsumerDetails();
        KeyWallet wallet = KeyWallet.builder()
                .id(UUID.randomUUID())
                .consumer(consumer)
                .purchaseKeys(purchaseKeys)
                .blockedPurchaseKeys(0L)
                .connectivityKeys(connectivityKeys)
                .blockedConnectivityKeys(0L)
                .build();
        return wallet;
    }

    private KeyTransaction creditTx(KeyWallet wallet, long purchaseDelta, long connectivityDelta,
                                    ZonedDateTime expiresAt) {
        return KeyTransaction.builder()
                .id(UUID.randomUUID())
                .keyWallet(wallet)
                .purchaseKeysDelta(purchaseDelta)
                .connectivityKeysDelta(connectivityDelta)
                .expiresAt(expiresAt)
                .expiryProcessed(false)
                .build();
    }

    // ─── No expired keys ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("when no expired transactions exist")
    class NoExpiredKeys {

        @Test
        @DisplayName("does nothing and makes no treasury call")
        void doesNothingWhenNoExpiredKeys() {
            when(keyTransactionRepository.findExpiredNotProcessed(any())).thenReturn(List.of());

            service.processExpiredKeys();

            verify(keyWalletRepository, never()).save(any());
            verify(keyTransactionRepository, never()).save(any());
            verify(treasuryService, never()).moveExpiredKeysToFortification(any(), any());
        }
    }

    // ─── Single wallet ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("when one wallet has expired purchase keys")
    class SingleWalletExpiry {

        @Test
        @DisplayName("expires only available keys (Math.min guard)")
        void expiresOnlyAvailableKeys() {
            ZonedDateTime past = ZonedDateTime.now().minusDays(1);
            // Wallet has 5 purchase keys but 10 keys credited (already spent 5)
            KeyWallet wallet = walletWithBalance(5L, 0L);

            KeyTransaction tx1 = creditTx(wallet, 6L, 0L, past);
            KeyTransaction tx2 = creditTx(wallet, 4L, 0L, past);
            // purchaseSum = 10, but wallet only has 5 — Math.min(5, 10) = 5

            when(keyTransactionRepository.findExpiredNotProcessed(any()))
                    .thenReturn(List.of(tx1, tx2));
            when(keyWalletRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(keyTransactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.processExpiredKeys();

            // Wallet should have 0 purchase keys left
            assertThat(wallet.getPurchaseKeys()).isEqualTo(0L);
            verify(keyWalletRepository).save(wallet);
        }

        @Test
        @DisplayName("calls treasury with correct COP amount (keys * keyValueCents)")
        void callsTreasuryWithCorrectAmount() {
            ZonedDateTime past = ZonedDateTime.now().minusDays(1);
            KeyWallet wallet = walletWithBalance(10L, 0L);
            KeyTransaction tx = creditTx(wallet, 10L, 0L, past);

            when(keyTransactionRepository.findExpiredNotProcessed(any()))
                    .thenReturn(List.of(tx));
            when(keyWalletRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(keyTransactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.processExpiredKeys();

            // 10 keys * 1000 centavos/key = 10_000 centavos
            verify(treasuryService).moveExpiredKeysToFortification(eq(10_000L), any(UUID.class));
        }

        @Test
        @DisplayName("marks source transactions as processed")
        void marksTransactionsAsProcessed() {
            ZonedDateTime past = ZonedDateTime.now().minusDays(1);
            KeyWallet wallet = walletWithBalance(5L, 0L);
            UUID txId1 = UUID.randomUUID();
            UUID txId2 = UUID.randomUUID();

            KeyTransaction tx1 = KeyTransaction.builder()
                    .id(txId1).keyWallet(wallet)
                    .purchaseKeysDelta(3L).connectivityKeysDelta(0L)
                    .expiresAt(past).expiryProcessed(false).build();
            KeyTransaction tx2 = KeyTransaction.builder()
                    .id(txId2).keyWallet(wallet)
                    .purchaseKeysDelta(2L).connectivityKeysDelta(0L)
                    .expiresAt(past).expiryProcessed(false).build();

            when(keyTransactionRepository.findExpiredNotProcessed(any()))
                    .thenReturn(List.of(tx1, tx2));
            when(keyWalletRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(keyTransactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.processExpiredKeys();

            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<UUID>> captor = ArgumentCaptor.forClass(List.class);
            verify(keyTransactionRepository).markAllAsProcessed(captor.capture());
            assertThat(captor.getValue()).containsExactlyInAnyOrder(txId1, txId2);
        }

        @Test
        @DisplayName("does not call treasury when wallet balance was already zero (all keys spent)")
        void noTreasuryCallWhenWalletAlreadyEmpty() {
            ZonedDateTime past = ZonedDateTime.now().minusDays(1);
            // Wallet has 0 purchase keys — all were spent before expiry
            KeyWallet wallet = walletWithBalance(0L, 0L);
            KeyTransaction tx = creditTx(wallet, 5L, 0L, past);

            when(keyTransactionRepository.findExpiredNotProcessed(any()))
                    .thenReturn(List.of(tx));

            service.processExpiredKeys();

            // actualPurchaseExpiry = Math.min(0, 5) = 0 → skip treasury call
            verify(keyWalletRepository, never()).save(any());
            verify(keyTransactionRepository).markAllAsProcessed(anyList());
            verify(treasuryService, never()).moveExpiredKeysToFortification(any(), any());
        }
    }

    // ─── Multiple wallets ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("when multiple wallets have expired keys")
    class MultipleWallets {

        @Test
        @DisplayName("groups transactions by wallet and processes each independently")
        void groupsByWallet() {
            ZonedDateTime past = ZonedDateTime.now().minusDays(1);
            KeyWallet walletA = walletWithBalance(3L, 0L);
            KeyWallet walletB = walletWithBalance(7L, 2L);

            KeyTransaction txA = creditTx(walletA, 3L, 0L, past);
            KeyTransaction txB1 = creditTx(walletB, 5L, 0L, past);
            KeyTransaction txB2 = creditTx(walletB, 2L, 2L, past);

            when(keyTransactionRepository.findExpiredNotProcessed(any()))
                    .thenReturn(List.of(txA, txB1, txB2));
            when(keyWalletRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(keyTransactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.processExpiredKeys();

            // Both wallets saved
            verify(keyWalletRepository, times(2)).save(any());
            // walletA: 3 expired. walletB: 7+2=9 purchase, but sum=7 → Math.min(7,7)=7; connectivity Math.min(2,2)=2
            assertThat(walletA.getPurchaseKeys()).isEqualTo(0L);
            assertThat(walletB.getPurchaseKeys()).isEqualTo(0L);
            assertThat(walletB.getConnectivityKeys()).isEqualTo(0L);

            // Treasury call: (3 + 7 + 2) * 1000 = 12_000
            verify(treasuryService).moveExpiredKeysToFortification(eq(12_000L), any(UUID.class));
        }

        @Test
        @DisplayName("continues processing other wallets when one fails")
        void continuesOnSingleWalletError() {
            ZonedDateTime past = ZonedDateTime.now().minusDays(1);
            KeyWallet walletA = walletWithBalance(3L, 0L);
            KeyWallet walletB = walletWithBalance(5L, 0L);

            KeyTransaction txA = creditTx(walletA, 3L, 0L, past);
            KeyTransaction txB = creditTx(walletB, 5L, 0L, past);

            when(keyTransactionRepository.findExpiredNotProcessed(any()))
                    .thenReturn(List.of(txA, txB));

            // walletA save throws; walletB should still process
            when(keyWalletRepository.save(walletA)).thenThrow(new RuntimeException("DB error"));
            when(keyWalletRepository.save(walletB)).thenAnswer(inv -> inv.getArgument(0));
            when(keyTransactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // Should not propagate exception
            service.processExpiredKeys();

            verify(keyWalletRepository, times(2)).save(any());
        }
    }

    // ─── Connectivity keys ────────────────────────────────────────────────────

    @Nested
    @DisplayName("connectivity keys expiry")
    class ConnectivityKeys {

        @Test
        @DisplayName("expires connectivity keys independently from purchase keys")
        void expiresConnectivityKeys() {
            ZonedDateTime past = ZonedDateTime.now().minusDays(1);
            KeyWallet wallet = walletWithBalance(0L, 8L);
            KeyTransaction tx = creditTx(wallet, 0L, 8L, past);

            when(keyTransactionRepository.findExpiredNotProcessed(any()))
                    .thenReturn(List.of(tx));
            when(keyWalletRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(keyTransactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.processExpiredKeys();

            assertThat(wallet.getConnectivityKeys()).isEqualTo(0L);
            // connectivity keys have same value as purchase keys in this config
            verify(treasuryService).moveExpiredKeysToFortification(eq(8_000L), any(UUID.class));
        }
    }
}
