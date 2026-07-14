package com.verygana2.models.finance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.verygana2.exceptions.InsufficientFundsException;
import com.verygana2.models.enums.finance.WalletStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests de la entidad {@link Wallet}: el presupuesto publicitario del
 * comercial (depósitos, consumos) y el cálculo dinámico de su status según
 * el umbral de alerta relativo al último depósito.
 */
@DisplayName("Wallet (entidad)")
class WalletTest {

    @Test
    @DisplayName("deposit: suma el monto y recalcula el status a ACTIVE si estaba EXHAUSTED")
    void deposit_addsAmountAndReactivates() {
        Wallet wallet = new Wallet();
        wallet.setBalanceCents(0L);
        wallet.setStatus(WalletStatus.EXHAUSTED);

        wallet.deposit(500_000L);

        assertThat(wallet.getBalanceCents()).isEqualTo(500_000L);
        assertThat(wallet.getStatus()).isEqualTo(WalletStatus.ACTIVE);
    }

    @Test
    @DisplayName("deposit con monto no positivo: lanza IllegalArgumentException")
    void deposit_nonPositiveAmount_throws() {
        Wallet wallet = new Wallet();
        assertThatThrownBy(() -> wallet.deposit(0L)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("consume: resta el monto si hay fondos suficientes")
    void consume_subtractsAmountWhenSufficient() {
        Wallet wallet = new Wallet();
        wallet.setBalanceCents(100_000L);
        wallet.setStatus(WalletStatus.ACTIVE);

        wallet.consume(30_000L);

        assertThat(wallet.getBalanceCents()).isEqualTo(70_000L);
    }

    @Test
    @DisplayName("consume sin fondos suficientes: lanza InsufficientFundsException")
    void consume_insufficientFunds_throws() {
        Wallet wallet = new Wallet();
        wallet.setBalanceCents(10_000L);

        assertThatThrownBy(() -> wallet.consume(20_000L)).isInstanceOf(InsufficientFundsException.class);
    }

    @Test
    @DisplayName("recalculateStatus: EXHAUSTED en 0, LOW_BALANCE bajo el umbral, ACTIVE por encima")
    void recalculateStatus_reflectsThresholds() {
        Wallet wallet = new Wallet();
        wallet.setLastDepositAmountCents(1_000_000L);
        wallet.setLowBalanceThresholdPct(10); // umbral = 100.000

        wallet.setBalanceCents(0L);
        wallet.recalculateStatus();
        assertThat(wallet.getStatus()).isEqualTo(WalletStatus.EXHAUSTED);

        wallet.setBalanceCents(50_000L); // < 100.000
        wallet.recalculateStatus();
        assertThat(wallet.getStatus()).isEqualTo(WalletStatus.LOW_BALANCE);

        wallet.setBalanceCents(500_000L); // > 100.000
        wallet.recalculateStatus();
        assertThat(wallet.getStatus()).isEqualTo(WalletStatus.ACTIVE);
    }

    @Test
    @DisplayName("getLowBalanceThresholdCents: 0 si nunca ha habido depósito")
    void getLowBalanceThresholdCents_zeroWithoutDeposit() {
        Wallet wallet = new Wallet();
        wallet.setLastDepositAmountCents(null);

        assertThat(wallet.getLowBalanceThresholdCents()).isZero();
    }

    @Test
    @DisplayName("isOperational: true para ACTIVE y LOW_BALANCE, false para EXHAUSTED/INACTIVE")
    void isOperational_trueForActiveAndLowBalance() {
        Wallet wallet = new Wallet();

        wallet.setStatus(WalletStatus.ACTIVE);
        assertThat(wallet.isOperational()).isTrue();

        wallet.setStatus(WalletStatus.LOW_BALANCE);
        assertThat(wallet.isOperational()).isTrue();

        wallet.setStatus(WalletStatus.EXHAUSTED);
        assertThat(wallet.isOperational()).isFalse();
    }

    @Test
    @DisplayName("prePersist (hook @PrePersist): valores por defecto cuando vienen null")
    void prePersist_appliesDefaults() {
        Wallet wallet = new Wallet();

        wallet.prePersist();

        assertThat(wallet.getBalanceCents()).isZero();
        assertThat(wallet.getStatus()).isEqualTo(WalletStatus.INACTIVE);
        assertThat(wallet.getCreatedAt()).isNotNull();
    }
}
