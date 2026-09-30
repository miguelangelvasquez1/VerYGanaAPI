package com.verygana2.models.finance.prosperity;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import org.hibernate.annotations.Immutable;

import com.verygana2.models.finance.plans.Investment;
import com.verygana2.models.finance.plans.Plan;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Umbral de Prosperidad generado por UNA Inversión Computable (Contrato B 2.31, 5.7, 10.3).
 * Conserva el origen individual de cada Umbral aunque el Saldo sea agregado — no se
 * aplica FIFO/LIFO entre Umbrales (Guía 5.8).
 *
 * Inmutable: el multiplicador queda como snapshot para que una reversión (10.20) use
 * la regla realmente aplicada a esta inversión, aunque la feature del plan cambie después.
 */
@Entity
@Immutable
@Table(name = "prosperity_thresholds")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProsperityThreshold {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false, updatable = false)
    private ProsperityAccount account;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "investment_id", nullable = false, unique = true, updatable = false)
    private Investment investment;

    /** Inversión Computable: valor neto antes de IVA y otros tributos. */
    @Column(name = "investment_net_cents", nullable = false, updatable = false)
    private Long investmentNetCents;

    @Column(name = "multiplier", nullable = false, updatable = false)
    private Integer multiplier;

    @Column(name = "generated_cents", nullable = false, updatable = false)
    private Long generatedCents;

    /** Plan (y su versión) del que se tomó el multiplicador: regla + versión aplicada. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false, updatable = false)
    private Plan plan;

    @Column(name = "plan_version", nullable = false, updatable = false)
    private Integer planVersion;

    /** Fecha/hora de validación de la inversión: desde aquí el Umbral tiene efecto. */
    @Column(name = "validated_at", nullable = false, updatable = false)
    private ZonedDateTime validatedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    public static ProsperityThreshold of(ProsperityAccount account, Investment investment,
            int multiplier, ZonedDateTime validatedAt) {
        ProsperityThreshold threshold = new ProsperityThreshold();
        threshold.account = account;
        threshold.investment = investment;
        threshold.investmentNetCents = investment.getDepositAmountCents();
        threshold.multiplier = multiplier;
        threshold.generatedCents = Math.multiplyExact(investment.getDepositAmountCents().longValue(), (long) multiplier);
        threshold.plan = investment.getPlanAtDeposit();
        threshold.planVersion = investment.getPlanAtDeposit().getVersion();
        threshold.validatedAt = validatedAt;
        return threshold;
    }

    @PrePersist
    void prePersist() {
        this.createdAt = ZonedDateTime.now(ZoneOffset.UTC);
    }
}
