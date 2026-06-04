package com.verygana2.models.finance;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import com.verygana2.exceptions.InsufficientFundsException;
import com.verygana2.models.enums.finance.WalletStatus;
import com.verygana2.models.finance.plans.BudgetTransaction;
import com.verygana2.models.finance.plans.Investment;
import com.verygana2.models.userDetails.CommercialDetails;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Data;

@Entity
@Table(name = "wallets")
@Data
public class Wallet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private long version;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "commercial_id", nullable = false, unique = true)
    private CommercialDetails commercial;

    @OneToMany(mappedBy = "wallet", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    private List<Investment> investments = new ArrayList<>();

    @OneToMany(mappedBy = "wallet", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    private List<BudgetTransaction> budgetTransactions = new ArrayList<>();

    @Column(name = "balance_cents", nullable = false)
    private Long balanceCents = 0L;

    /**
     * Estado operativo del presupuesto.
     * Independiente del plan — un PREMIUM puede estar EXHAUSTED.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WalletStatus status;

    /**
     * Monto del último depósito confirmado en centavos.
     * Usado para calcular el umbral de alerta dinámicamente:
     * threshold = lastDepositAmountCents × lowBalanceThresholdPct / 100
     */
    @Column(name = "last_deposit_amount_cents")
    private Long lastDepositAmountCents;

    /**
     * Umbral de alerta como porcentaje del último depósito.
     * Cuando balance cae por debajo de este porcentaje → LOW_BALANCE.
     * Default 10% — configurable por admin.
     * Garantiza que siempre haya saldo para pagar recompensas en curso.
     */
    @Column(name = "low_balance_threshold_pct", nullable = false)
    private int lowBalanceThresholdPct = 10;

    @Column(name = "last_updated", nullable = false)
    private ZonedDateTime lastUpdated;

    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    @PrePersist
    public void prePersist() {
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        if (this.createdAt == null)
            this.createdAt = now;
        if (this.lastUpdated == null)
            this.lastUpdated = now;
        if (this.balanceCents == null)
            this.balanceCents = 0L;
        if (this.status == null) {
            this.status = WalletStatus.INACTIVE;
        }
    }

    @PreUpdate
    public void preUpdate() {
        this.lastUpdated = ZonedDateTime.now(ZoneOffset.UTC);
    }

    public static Wallet createFor(CommercialDetails commercial) {
        Wallet wallet = new Wallet();
        wallet.setCommercial(commercial);
        return wallet;
    }

    public void deposit(Long amount) {
        if (amount <= 0)
            throw new IllegalArgumentException("Amount must be positive");
        this.balanceCents += amount;
        recalculateStatus();
    }

    public void consume(Long amount) {
        if (amount <= 0)
            throw new IllegalArgumentException("Amount must be positive");
        if (!hasFundsFor(amount))
            throw new InsufficientFundsException();
        this.balanceCents -= amount;
        recalculateStatus();
    }

    public boolean hasFundsFor(Long amount) {
        return this.balanceCents >= amount;
    }

    public boolean isOperational() {
        return status == WalletStatus.ACTIVE || status == WalletStatus.LOW_BALANCE;
    }

    public boolean isExhausted() {
        return this.balanceCents == 0L;
    }

    public void recalculateStatus() {
        if (balanceCents == 0)
            this.status = WalletStatus.EXHAUSTED;
        else if (balanceCents < getLowBalanceThresholdCents())
            this.status = WalletStatus.LOW_BALANCE;
        else
            this.status = WalletStatus.ACTIVE;
    }

    /**
     * Calcula el umbral de alerta en centavos.
     * Si nunca ha habido un depósito, el umbral es 0.
     */
    public long getLowBalanceThresholdCents() {
        if (lastDepositAmountCents == null || lastDepositAmountCents == 0)
            return 0L;
        return lastDepositAmountCents * lowBalanceThresholdPct / 100;
    }
}
