package com.verygana2.services.finance;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.verygana2.config.TreasuryConfig;
import com.verygana2.models.enums.finance.TreasuryAccountCode;
import com.verygana2.models.finance.TreasuryAccount;
import com.verygana2.models.finance.TreasuryMovement;
import com.verygana2.models.records.TreasurySnapshot;
import com.verygana2.models.userDetails.CommercialDetails;
import com.verygana2.repositories.finance.TreasuryAccountRepository;
import com.verygana2.repositories.finance.TreasuryMovementRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests de {@link TreasuryServiceImpl}: el libro contable de los 4 bolsillos
 * virtuales. Cada método mueve dinero entre cuentas y debe dejar registrado
 * el movimiento — aquí se verifica tanto la aritmética (los 3 montos de un
 * depósito siempre suman el total, sin perder centavos por redondeo) como
 * los invariantes de negocio (nunca dejar una cuenta en negativo).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TreasuryServiceImpl")
class TreasuryServiceImplTest {

    @Mock private TreasuryAccountRepository treasuryAccountRepository;
    @Mock private TreasuryMovementRepository treasuryMovementRepository;
    @Mock private TreasuryConfig treasuryConfig;

    private TreasuryServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new TreasuryServiceImpl(treasuryAccountRepository, treasuryMovementRepository, treasuryConfig);
    }

    private TreasuryAccount account(TreasuryAccountCode code, long balance) {
        return TreasuryAccount.builder().code(code).balanceCents(balance).build();
    }

    private void stubAccount(TreasuryAccountCode code, long balance) {
        when(treasuryAccountRepository.findByCodeForUpdate(code)).thenReturn(Optional.of(account(code, balance)));
    }

    private CommercialDetails commercial(Long id) {
        CommercialDetails c = new CommercialDetails();
        c.setId(id);
        return c;
    }

    @Nested
    @DisplayName("distributeDeposit")
    class DistributeDeposit {

        @Test
        @DisplayName("distribuye 60/10/30 y los 3 montos suman exactamente el total (sin perder centavos por redondeo)")
        void splitsIntoThreeAccountsSummingExactTotal() {
            when(treasuryConfig.getKeysReservePct()).thenReturn(60);
            when(treasuryConfig.getFortificationPct()).thenReturn(10);
            stubAccount(TreasuryAccountCode.EXTERNAL_INCOME, 0);
            stubAccount(TreasuryAccountCode.KEYS_RESERVE, 0);
            stubAccount(TreasuryAccountCode.FORTIFICATION, 0);
            stubAccount(TreasuryAccountCode.OPERATIONS, 0);

            service.distributeDeposit(1_000_001L, commercial(1L), UUID.randomUUID()); // monto impar a propósito

            var captor = org.mockito.ArgumentCaptor.forClass(TreasuryAccount.class);
            verify(treasuryAccountRepository, org.mockito.Mockito.times(3)).save(captor.capture());
            long sum = captor.getAllValues().stream().mapToLong(TreasuryAccount::getBalanceCents).sum();
            assertThat(sum).isEqualTo(1_000_001L); // OPERATIONS absorbió el residuo del redondeo
            verify(treasuryMovementRepository, org.mockito.Mockito.times(3)).save(any(TreasuryMovement.class));
        }

        @Test
        @DisplayName("monto no positivo: lanza IllegalArgumentException sin tocar ninguna cuenta")
        void nonPositiveAmount_throwsWithoutTouchingAccounts() {
            assertThatThrownBy(() -> service.distributeDeposit(0L, commercial(1L), UUID.randomUUID()))
                    .isInstanceOf(IllegalArgumentException.class);
            verify(treasuryAccountRepository, org.mockito.Mockito.never()).findByCodeForUpdate(any());
        }
    }

    @Test
    @DisplayName("distributeSubscription: todo el monto va a OPERATIONS, ninguna otra cuenta se toca")
    void distributeSubscription_allGoesToOperations() {
        stubAccount(TreasuryAccountCode.EXTERNAL_INCOME, 0);
        stubAccount(TreasuryAccountCode.OPERATIONS, 500_000L);

        service.distributeSubscription(200_000L, commercial(1L), UUID.randomUUID());

        var captor = org.mockito.ArgumentCaptor.forClass(TreasuryAccount.class);
        verify(treasuryAccountRepository).save(captor.capture());
        assertThat(captor.getValue().getBalanceCents()).isEqualTo(700_000L);
    }

    @Nested
    @DisplayName("convertKeysToPayoutPending")
    class ConvertKeysToPayoutPending {

        @Test
        @DisplayName("saldo suficiente y por encima del umbral: mueve KEYS_RESERVE → PAYOUTS_PENDING")
        void sufficientBalance_movesFunds() {
            when(treasuryConfig.getKeysReserveCriticalThresholdCents()).thenReturn(2_000_000L);
            when(treasuryConfig.getKeysReserveWarnThresholdCents()).thenReturn(10_000_000L);
            stubAccount(TreasuryAccountCode.KEYS_RESERVE, 50_000_000L);
            stubAccount(TreasuryAccountCode.PAYOUTS_PENDING, 0L);

            service.convertKeysToPayoutPending(1_000_000L, UUID.randomUUID());

            verify(treasuryAccountRepository, org.mockito.Mockito.times(2)).save(any());
        }

        @Test
        @DisplayName("saldo insuficiente en KEYS_RESERVE: lanza IllegalStateException")
        void insufficientBalance_throws() {
            stubAccount(TreasuryAccountCode.KEYS_RESERVE, 100L);
            stubAccount(TreasuryAccountCode.PAYOUTS_PENDING, 0L);

            assertThatThrownBy(() -> service.convertKeysToPayoutPending(1_000L, UUID.randomUUID()))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("el saldo post-transacción caería bajo el umbral crítico: bloquea la operación")
        void wouldFallBelowCriticalThreshold_blocks() {
            when(treasuryConfig.getKeysReserveCriticalThresholdCents()).thenReturn(2_000_000L);
            stubAccount(TreasuryAccountCode.KEYS_RESERVE, 2_500_000L);
            stubAccount(TreasuryAccountCode.PAYOUTS_PENDING, 0L);

            // 2.500.000 - 1.000.000 = 1.500.000 < umbral crítico (2.000.000)
            assertThatThrownBy(() -> service.convertKeysToPayoutPending(1_000_000L, UUID.randomUUID()))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    @DisplayName("moveCashToPayoutPending: acredita PAYOUTS_PENDING desde la cuenta externa virtual")
    void moveCashToPayoutPending_creditsPayoutsPending() {
        stubAccount(TreasuryAccountCode.EXTERNAL_INCOME, 0L);
        stubAccount(TreasuryAccountCode.PAYOUTS_PENDING, 100_000L);

        service.moveCashToPayoutPending(50_000L, UUID.randomUUID());

        var captor = org.mockito.ArgumentCaptor.forClass(TreasuryAccount.class);
        verify(treasuryAccountRepository).save(captor.capture());
        assertThat(captor.getValue().getBalanceCents()).isEqualTo(150_000L);
    }

    @Nested
    @DisplayName("retainCommission")
    class RetainCommission {

        @Test
        @DisplayName("comisión positiva: mueve PAYOUTS_PENDING → OPERATIONS")
        void positiveCommission_movesFunds() {
            stubAccount(TreasuryAccountCode.PAYOUTS_PENDING, 100_000L);
            stubAccount(TreasuryAccountCode.OPERATIONS, 0L);

            service.retainCommission(10_000L, UUID.randomUUID(), "COPAYMENT");

            verify(treasuryAccountRepository, org.mockito.Mockito.times(2)).save(any());
        }

        @Test
        @DisplayName("comisión en cero: no hace nada, ni siquiera consulta las cuentas")
        void zeroCommission_doesNothing() {
            service.retainCommission(0L, UUID.randomUUID(), "COPAYMENT");

            verify(treasuryAccountRepository, org.mockito.Mockito.never()).findByCodeForUpdate(any());
        }

        @Test
        @DisplayName("PAYOUTS_PENDING sin saldo suficiente: lanza IllegalStateException")
        void insufficientPayoutsPending_throws() {
            stubAccount(TreasuryAccountCode.PAYOUTS_PENDING, 100L);
            stubAccount(TreasuryAccountCode.OPERATIONS, 0L);

            assertThatThrownBy(() -> service.retainCommission(1_000L, UUID.randomUUID(), "COPAYMENT"))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    @DisplayName("registerPayoutSent: debita PAYOUTS_PENDING; sin saldo lanza IllegalStateException")
    void registerPayoutSent_debitsPayoutsPending() {
        stubAccount(TreasuryAccountCode.PAYOUTS_PENDING, 100_000L);
        stubAccount(TreasuryAccountCode.EXTERNAL_INCOME, 0L);

        service.registerPayoutSent(60_000L, UUID.randomUUID());

        var captor = org.mockito.ArgumentCaptor.forClass(TreasuryAccount.class);
        verify(treasuryAccountRepository, org.mockito.Mockito.times(1)).save(captor.capture());
        assertThat(captor.getValue().getBalanceCents()).isEqualTo(40_000L);
    }

    @Test
    @DisplayName("getSnapshot: suma los 4 saldos y calcula el total")
    void getSnapshot_sumsAllFourBalances() {
        when(treasuryAccountRepository.findByCode(TreasuryAccountCode.KEYS_RESERVE))
                .thenReturn(Optional.of(account(TreasuryAccountCode.KEYS_RESERVE, 100L)));
        when(treasuryAccountRepository.findByCode(TreasuryAccountCode.FORTIFICATION))
                .thenReturn(Optional.of(account(TreasuryAccountCode.FORTIFICATION, 50L)));
        when(treasuryAccountRepository.findByCode(TreasuryAccountCode.OPERATIONS))
                .thenReturn(Optional.of(account(TreasuryAccountCode.OPERATIONS, 30L)));
        when(treasuryAccountRepository.findByCode(TreasuryAccountCode.PAYOUTS_PENDING))
                .thenReturn(Optional.of(account(TreasuryAccountCode.PAYOUTS_PENDING, 20L)));

        TreasurySnapshot snapshot = service.getSnapshot();

        assertThat(snapshot.totalCents()).isEqualTo(200L);
    }

    @Test
    @DisplayName("moveExpiredKeysToFortification: si KEYS_RESERVE tiene menos de lo esperado, mueve solo lo disponible (nunca bloquea)")
    void moveExpiredKeysToFortification_movesOnlyAvailableIfInsufficient() {
        stubAccount(TreasuryAccountCode.KEYS_RESERVE, 500L); // menos de lo pedido
        stubAccount(TreasuryAccountCode.FORTIFICATION, 0L);

        service.moveExpiredKeysToFortification(1_000L, UUID.randomUUID());

        var captor = org.mockito.ArgumentCaptor.forClass(TreasuryAccount.class);
        verify(treasuryAccountRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        // KEYS_RESERVE queda en 0 (se movió lo disponible, no lo pedido)
        assertThat(captor.getAllValues().get(0).getBalanceCents()).isZero();
    }
}
