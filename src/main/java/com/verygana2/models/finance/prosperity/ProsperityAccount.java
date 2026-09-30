package com.verygana2.models.finance.prosperity;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import com.verygana2.models.enums.finance.ProsperityEntryType;
import com.verygana2.models.userDetails.CommercialDetails;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Cuenta económica de prosperidad de un comercial Tipo B (Contrato B 2.32, 10.6).
 *
 * Es una proyección del libro {@link ProsperityLedgerEntry}: sus totales solo cambian
 * a través de {@link #apply}, que invoca exclusivamente ProsperityServiceImpl al
 * registrar un asiento. La conciliación diaria verifica que coincidan con el libro.
 *
 * El Saldo de Prosperidad NO es dinero, ni saldo retirable, ni crédito: es la porción
 * del Umbral acumulado que todavía puede absorber Ventas Computables.
 *
 * Nunca se borra ni se reinicia: si el comercial deja de ser STANDARD, la cuenta
 * queda congelada (no absorbe ventas) y se reactiva tal cual si vuelve a STANDARD.
 */
@Entity
@Table(name = "prosperity_accounts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProsperityAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "commercial_id", nullable = false, unique = true, updatable = false)
    private CommercialDetails commercial;

    @Column(name = "balance_cents", nullable = false)
    private Long balanceCents = 0L;

    @Column(name = "accumulated_threshold_cents", nullable = false)
    private Long accumulatedThresholdCents = 0L;

    @Column(name = "total_absorbed_cents", nullable = false)
    private Long totalAbsorbedCents = 0L;

    @Column(name = "total_reintegrated_cents", nullable = false)
    private Long totalReintegratedCents = 0L;

    /** Último número de secuencia usado en el libro de esta cuenta. */
    @Column(name = "last_sequence", nullable = false)
    private Long lastSequence = 0L;

    @Version
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    @Column(name = "last_updated", nullable = false)
    private ZonedDateTime lastUpdated;

    public static ProsperityAccount openFor(CommercialDetails commercial) {
        ProsperityAccount account = new ProsperityAccount();
        account.commercial = commercial;
        return account;
    }

    @PrePersist
    void prePersist() {
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        this.createdAt = now;
        this.lastUpdated = now;
    }

    @PreUpdate
    void preUpdate() {
        this.lastUpdated = ZonedDateTime.now(ZoneOffset.UTC);
    }

    /** Siguiente número de secuencia para un asiento nuevo. */
    long nextSequence() {
        this.lastSequence = this.lastSequence + 1;
        return this.lastSequence;
    }

    /**
     * Aplica el efecto de un asiento ya validado sobre los totales de la cuenta.
     * Package-private: solo {@link ProsperityLedgerEntry#record} lo usa.
     */
    void apply(ProsperityEntryType type, long amountCents) {
        this.balanceCents = this.balanceCents + type.signedAmount(amountCents);
        switch (type) {
            case THRESHOLD_GENERATED -> this.accumulatedThresholdCents += amountCents;
            case THRESHOLD_REVERSAL -> this.accumulatedThresholdCents -= amountCents;
            case SALE_ABSORPTION -> this.totalAbsorbedCents += amountCents;
            case REFUND_REINTEGRATION -> this.totalReintegratedCents += amountCents;
            case ADJUSTMENT_CREDIT, ADJUSTMENT_DEBIT -> { /* solo afectan el saldo */ }
        }
    }
}
