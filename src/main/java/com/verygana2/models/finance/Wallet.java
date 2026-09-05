package com.verygana2.models.finance;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import com.verygana2.exceptions.InsufficientFundsException;
import com.verygana2.models.enums.finance.WalletBudgetAlertStage;
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
     * Monto del último depósito/recarga confirmado, en centavos. Es la referencia
     * sobre la que {@code EffectivePlanResolver.resolveBudgetThresholds} calcula los
     * umbrales de aviso de saldo bajo (WARNING / CRITICAL) como % de este monto.
     * Se registra solo en recargas reales (ver {@link #registerDeposit(Long)}), nunca
     * en reembolsos de presupuesto no consumido.
     */
    @Column(name = "last_deposit_amount_cents")
    private Long lastDepositAmountCents;

    /** Última etapa de aviso de saldo bajo notificada — evita reenviar el mismo aviso cada barrido. */
    @Enumerated(EnumType.STRING)
    @Column(name = "last_budget_alert_stage", nullable = false, length = 20)
    private WalletBudgetAlertStage lastBudgetAlertStage = WalletBudgetAlertStage.NONE;

    @Column(name = "last_budget_alert_at")
    private ZonedDateTime lastBudgetAlertAt;

    /**
     * Momento en que el saldo llegó a 0. Se sella la primera vez que el balance
     * cae a cero y se limpia en cuanto vuelve a haber saldo. Permite medir cuánto
     * lleva la billetera agotada para escalar a estado DORMANT (bloqueo de edición)
     * pasado el periodo de gracia del plan.
     */
    @Column(name = "exhausted_since")
    private ZonedDateTime exhaustedSince;

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
        if (this.lastBudgetAlertStage == null) {
            this.lastBudgetAlertStage = WalletBudgetAlertStage.NONE;
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

    /**
     * Acredita saldo en la wallet sin más semántica: lo usan tanto las recargas
     * confirmadas (vía {@link #registerDeposit(Long)}) como los reembolsos de
     * presupuesto no consumido de un activo. Los reembolsos NO deben alterar
     * {@code lastDepositAmountCents} — de ahí que ese campo no se toque aquí.
     */
    public void deposit(Long amount) {
        if (amount <= 0) throw new IllegalArgumentException("Amount must be positive");
        this.balanceCents += amount;
        recalculateStatus();
        if (this.balanceCents > 0) {
            // Saldo repuesto — permite que el próximo ciclo de avisos de saldo bajo se dispare de nuevo.
            this.lastBudgetAlertStage = WalletBudgetAlertStage.NONE;
        }
    }

    /**
     * Acredita una recarga/depósito confirmado del comercial. Además de sumar el
     * saldo, registra el monto en {@code lastDepositAmountCents} como referencia
     * para los umbrales de aviso de saldo bajo
     * ({@code EffectivePlanResolver.resolveBudgetThresholds}).
     */
    public void registerDeposit(Long amount) {
        if (amount <= 0) throw new IllegalArgumentException("Amount must be positive");
        this.lastDepositAmountCents = amount;
        deposit(amount);
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
        return status == WalletStatus.ACTIVE;
    }

    public boolean isExhausted() {
        return this.balanceCents == 0L;
    }

    /**
     * Estado operativo persistido: solo {@code EXHAUSTED} (saldo 0) vs {@code ACTIVE}.
     * El "saldo bajo" NO es un estado — se deriva en tiempo real de los umbrales por
     * plan (ver {@code EffectivePlanResolver.resolveBudgetThresholds} y
     * {@code BudgetAlertScheduler}), igual que {@code budgetSuspended}/{@code budgetDormant}.
     */
    public void recalculateStatus() {
        if (balanceCents == 0) {
            this.status = WalletStatus.EXHAUSTED;
            if (this.exhaustedSince == null) {
                this.exhaustedSince = ZonedDateTime.now(ZoneOffset.UTC);
            }
        } else {
            this.status = WalletStatus.ACTIVE;
            this.exhaustedSince = null;
        }
    }
}
