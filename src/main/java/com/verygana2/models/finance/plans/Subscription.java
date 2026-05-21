package com.verygana2.models.finance.plans;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.UUID;

import com.verygana2.models.enums.finance.plans.SubscriptionStatus;
import com.verygana2.models.finance.WompiTransaction;
import com.verygana2.models.userDetails.CommercialDetails;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "subscriptions", indexes = {
        @Index(name = "idx_sub_commercial_id",   columnList = "commercial_id"),
        @Index(name = "idx_sub_status",          columnList = "status"),
        @Index(name = "idx_sub_end_date",        columnList = "end_date"),
        @Index(name = "idx_sub_wompi_reference", columnList = "wompi_reference")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Subscription {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "commercial_id", nullable = false)
    @NotNull
    private CommercialDetails commercial;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false)
    @NotNull
    private Plan plan;

    /**
     * Referencia Wompi guardada al crear el checkout.
     * Permite al webhook encontrar esta Subscription por referencia
     * sin necesidad de tener el commercial en WompiTransaction.
     *
     * Flujo:
     *   1. PlanService genera reference = "VG-SUB-{id}-{timestamp}"
     *   2. Crea Subscription(PENDING_PAYMENT) con ese wompiReference
     *   3. Wompi confirma → webhook busca por wompiReference → encuentra Subscription
     *   4. Activa la Subscription y vincula la WompiTransaction
     *
     * Indexado para que el lookup por webhook sea O(log n).
     */
    @Column(name = "wompi_reference", unique = true, length = 100)
    private String wompiReference;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wompi_transaction_id")
    private WompiTransaction wompiTransaction;

    @Column(name = "amount_paid_cents", nullable = false)
    @Positive
    @NotNull
    private Long amountPaidCents;

    @Column(name = "start_date")
    private ZonedDateTime startDate;

    @Column(name = "end_date")
    private ZonedDateTime endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 25)
    @NotNull
    @Builder.Default
    private SubscriptionStatus status = SubscriptionStatus.PENDING_PAYMENT;

    @Column(name = "terminated_at")
    private ZonedDateTime terminatedAt;

    @Column(name = "cancellation_reason", length = 500)
    private String cancellationReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = ZonedDateTime.now(ZoneOffset.UTC);
    }

    // ─── Métodos de negocio ───────────────────────────────────────────────────

    /**
     * Activa la suscripción cuando Wompi confirma el pago.
     * Fija el período de 1 mes desde el momento de confirmación.
     */
    public void activate(WompiTransaction wompiTx) {
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        this.wompiTransaction = wompiTx;
        this.status = SubscriptionStatus.ACTIVE;
        this.startDate = now;
        this.endDate = now.plusMonths(1);
    }

    public boolean isCurrentlyActive() {
        return status == SubscriptionStatus.ACTIVE
                && ZonedDateTime.now(ZoneOffset.UTC).isBefore(endDate);
    }

    public long daysRemaining() {
        if (endDate == null) return 0;
        return java.time.temporal.ChronoUnit.DAYS.between(
                ZonedDateTime.now(ZoneOffset.UTC), endDate);
    }

    public void expire() {
        this.status = SubscriptionStatus.EXPIRED;
        this.terminatedAt = ZonedDateTime.now(ZoneOffset.UTC);
    }

    public void markAsRenewed() {
        this.status = SubscriptionStatus.RENEWED;
        this.terminatedAt = ZonedDateTime.now(ZoneOffset.UTC);
    }

    public void cancel(String reason) {
        this.status = SubscriptionStatus.CANCELLED;
        this.terminatedAt = ZonedDateTime.now(ZoneOffset.UTC);
        this.cancellationReason = reason;
    }
}