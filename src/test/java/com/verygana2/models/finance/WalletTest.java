package com.verygana2.models.finance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.verygana2.exceptions.InsufficientFundsException;
import com.verygana2.models.enums.finance.WalletStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests de la entidad {@link Wallet}: el presupuesto publicitario del comercial
 * (depósitos, consumos) y su estado operativo (ACTIVE / EXHAUSTED).
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
    @DisplayName("registerDeposit: registra lastDepositAmountCents (referencia del umbral de saldo bajo)")
    void registerDeposit_recordsLastDepositAmount() {
        Wallet wallet = new Wallet();
        wallet.setBalanceCents(0L);

        wallet.registerDeposit(2_000_000L);

        assertThat(wallet.getBalanceCents()).isEqualTo(2_000_000L);
        assertThat(wallet.getLastDepositAmountCents()).isEqualTo(2_000_000L);
        assertThat(wallet.getStatus()).isEqualTo(WalletStatus.ACTIVE);
    }

    @Test
    @DisplayName("deposit (reembolso): NO altera lastDepositAmountCents")
    void deposit_doesNotTouchLastDepositAmount() {
        Wallet wallet = new Wallet();
        wallet.setBalanceCents(100_000L);
        wallet.setLastDepositAmountCents(2_000_000L);

        wallet.deposit(5_000L); // reembolso de presupuesto no consumido

        assertThat(wallet.getBalanceCents()).isEqualTo(105_000L);
        assertThat(wallet.getLastDepositAmountCents()).isEqualTo(2_000_000L);
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
    @DisplayName("recalculateStatus: EXHAUSTED en 0, ACTIVE con saldo > 0")
    void recalculateStatus_reflectsThresholds() {
        Wallet wallet = new Wallet();

        wallet.setBalanceCents(0L);
        wallet.recalculateStatus();
        assertThat(wallet.getStatus()).isEqualTo(WalletStatus.EXHAUSTED);

        wallet.setBalanceCents(1L);
        wallet.recalculateStatus();
        assertThat(wallet.getStatus()).isEqualTo(WalletStatus.ACTIVE);

        wallet.setBalanceCents(500_000L);
        wallet.recalculateStatus();
        assertThat(wallet.getStatus()).isEqualTo(WalletStatus.ACTIVE);
    }

    @Test
    @DisplayName("recalculateStatus: sella exhaustedSince al llegar a 0 y lo limpia al recuperar saldo")
    void recalculateStatus_tracksExhaustedSince() {
        Wallet wallet = new Wallet();

        wallet.setBalanceCents(0L);
        wallet.recalculateStatus();
        assertThat(wallet.getExhaustedSince()).isNotNull();

        var sealedAt = wallet.getExhaustedSince();
        wallet.recalculateStatus(); // sigue en 0 — no se re-sella
        assertThat(wallet.getExhaustedSince()).isEqualTo(sealedAt);

        wallet.setBalanceCents(500_000L);
        wallet.recalculateStatus();
        assertThat(wallet.getExhaustedSince()).isNull();
    }

    @Test
    @DisplayName("deposit tras agotamiento: limpia exhaustedSince")
    void deposit_clearsExhaustedSince() {
        Wallet wallet = new Wallet();
        wallet.setBalanceCents(0L);
        wallet.recalculateStatus();
        assertThat(wallet.getExhaustedSince()).isNotNull();

        wallet.deposit(500_000L);

        assertThat(wallet.getExhaustedSince()).isNull();
    }

    @Test
    @DisplayName("isOperational: true solo para ACTIVE, false para EXHAUSTED/INACTIVE")
    void isOperational_trueForActive() {
        Wallet wallet = new Wallet();

        wallet.setStatus(WalletStatus.ACTIVE);
        assertThat(wallet.isOperational()).isTrue();

        wallet.setStatus(WalletStatus.EXHAUSTED);
        assertThat(wallet.isOperational()).isFalse();

        wallet.setStatus(WalletStatus.INACTIVE);
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
