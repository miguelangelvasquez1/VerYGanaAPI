package com.verygana2.models.finance.prosperity;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import org.hibernate.annotations.Immutable;

import com.verygana2.exceptions.BusinessException;
import com.verygana2.models.enums.finance.ProsperityEntryType;
import com.verygana2.models.enums.finance.ProsperityOriginType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Asiento del Libro Mayor de Prosperidad (Contrato B 10.5, 10.27): append-only,
 * cronológico por cuenta ({@code sequence}) y con saldo antes/después.
 *
 * Nunca se edita ni se borra (entidad {@link Immutable} + triggers MySQL en V9):
 * toda corrección es un asiento compensatorio que apunta al original vía
 * {@code relatedEntry}.
 */
@Entity
@Immutable
@Table(name = "prosperity_ledger_entries")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProsperityLedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false, updatable = false)
    private ProsperityAccount account;

    @Column(name = "seq_no", nullable = false, updatable = false)
    private Long sequence;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, updatable = false)
    private ProsperityEntryType type;

    /** Valor del movimiento (positivo salvo una reversión sin saldo que retirar); la naturaleza la da {@link #type}. */
    @Column(name = "amount_cents", nullable = false, updatable = false)
    private Long amountCents;

    @Column(name = "balance_before_cents", nullable = false, updatable = false)
    private Long balanceBeforeCents;

    @Column(name = "balance_after_cents", nullable = false, updatable = false)
    private Long balanceAfterCents;

    @Enumerated(EnumType.STRING)
    @Column(name = "origin_type", nullable = false, updatable = false)
    private ProsperityOriginType originType;

    @Column(name = "origin_id", nullable = false, updatable = false, length = 64)
    private String originId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "threshold_id", updatable = false)
    private ProsperityThreshold threshold;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "related_entry_id", updatable = false)
    private ProsperityLedgerEntry relatedEntry;

    /** Venta Computable completa del ítem — solo en SALE_ABSORPTION. */
    @Column(name = "sale_amount_cents", updatable = false)
    private Long saleAmountCents;

    /**
     * Solo en THRESHOLD_REVERSAL: parte del Umbral reversado que ya había sido consumida
     * y no pudo retirarse del Saldo. Queda registrada para tratamiento contractual.
     */
    @Column(name = "uncovered_cents", updatable = false)
    private Long uncoveredCents;

    @Column(name = "idempotency_key", nullable = false, unique = true, updatable = false, length = 120)
    private String idempotencyKey;

    /** Fecha/hora efectiva de validación de la operación de origen. */
    @Column(name = "effective_at", nullable = false, updatable = false)
    private ZonedDateTime effectiveAt;

    @Column(name = "cause", length = 500, updatable = false)
    private String cause;

    @Column(name = "support_ref", length = 500, updatable = false)
    private String supportRef;

    /** Proceso automático (ej. "SYSTEM:COPAYMENT") o "ADMIN:{id}" del admin que registró el asiento. */
    @Column(name = "performed_by", nullable = false, length = 120, updatable = false)
    private String performedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    /** Datos de un asiento por registrar. Los saldos y la secuencia los calcula {@link #record}. */
    @Builder
    public record Draft(
            ProsperityEntryType type,
            long amountCents,
            ProsperityOriginType originType,
            String originId,
            ProsperityThreshold threshold,
            ProsperityLedgerEntry relatedEntry,
            Long saleAmountCents,
            Long uncoveredCents,
            String idempotencyKey,
            ZonedDateTime effectiveAt,
            String cause,
            String supportRef,
            String performedBy) {
    }

    /**
     * Crea el asiento y aplica su efecto sobre la cuenta en un solo paso, para que el
     * saldo de la cuenta y el {@code balanceAfter} del libro no puedan divergir.
     * El llamador debe tener la cuenta bloqueada (PESSIMISTIC_WRITE).
     *
     * @throws BusinessException si el monto no es positivo o el asiento dejaría el Saldo negativo.
     */
    public static ProsperityLedgerEntry record(ProsperityAccount account, Draft draft) {
        // Única excepción al monto positivo: la reversión de un Umbral ya consumido por
        // completo no retira nada del Saldo, pero debe quedar registrada (con uncoveredCents).
        boolean zeroAllowed = draft.type() == ProsperityEntryType.THRESHOLD_REVERSAL;
        if (draft.amountCents() < 0 || (draft.amountCents() == 0 && !zeroAllowed)) {
            throw new BusinessException("El valor de un asiento de prosperidad debe ser positivo");
        }
        long before = account.getBalanceCents();
        long after = before + draft.type().signedAmount(draft.amountCents());
        if (after < 0) {
            throw new BusinessException(String.format(
                    "El asiento %s de %d dejaría el Saldo de Prosperidad en negativo (saldo actual %d)",
                    draft.type(), draft.amountCents(), before));
        }

        ProsperityLedgerEntry entry = new ProsperityLedgerEntry();
        entry.account = account;
        entry.sequence = account.nextSequence();
        entry.type = draft.type();
        entry.amountCents = draft.amountCents();
        entry.balanceBeforeCents = before;
        entry.balanceAfterCents = after;
        entry.originType = draft.originType();
        entry.originId = draft.originId();
        entry.threshold = draft.threshold();
        entry.relatedEntry = draft.relatedEntry();
        entry.saleAmountCents = draft.saleAmountCents();
        entry.uncoveredCents = draft.uncoveredCents();
        entry.idempotencyKey = draft.idempotencyKey();
        entry.effectiveAt = draft.effectiveAt() != null ? draft.effectiveAt() : ZonedDateTime.now(ZoneOffset.UTC);
        entry.cause = draft.cause();
        entry.supportRef = draft.supportRef();
        entry.performedBy = draft.performedBy();

        account.apply(draft.type(), draft.amountCents());
        return entry;
    }

    @PrePersist
    void prePersist() {
        this.createdAt = ZonedDateTime.now(ZoneOffset.UTC);
    }
}
