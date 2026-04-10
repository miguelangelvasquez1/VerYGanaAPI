package com.verygana2.models.finance;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.UUID;

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
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.verygana2.models.enums.finance.PayoutStatus;
import com.verygana2.models.userDetails.CommercialDetails;

/**
 * Representa un pago batch diario a un empresario.
 *
 * DISEÑO DEL JOB DE PAYOUT (implementar en PayoutScheduler con @Scheduled):
 *
 * Hora de ejecución recomendada: 11:00 PM hora Colombia (04:00 UTC del día siguiente).
 * Razón: las 11 PM da tiempo a que todas las ventas del día estén confirmadas por Wompi
 * y los movimientos de tesorería estén COMPLETED. No usar medianoche porque los webhooks
 * de Wompi pueden tardar minutos en llegar.
 *
 * Algoritmo del job:
 *   1. Buscar todos los Copayment(status=COMPLETED) del día que no tengan Payout asociado.
 *   2. Agrupar por commercial_id (empresario dueño del producto vendido).
 *   3. Para cada grupo:
 *      a. Sumar todos los total_amount_cents → grossAmountCents
 *      b. Verificar si commercial.commissionActive = true
 *      c. Si true: commissionCents = grossAmountCents × commissionPct / 100
 *      d. Si false: commissionCents = 0 (aún no ha recuperado 6× su inversión)
 *      e. netAmountCents = grossAmountCents - commissionCents
 *      f. Crear Payout(status=SCHEDULED, scheduledAt=NOW())
 *      g. Mover commissionCents de PAYOUTS_PENDING → OPERATIONS (TreasuryMovement)
 *   4. Para cada Payout(status=SCHEDULED):
 *      a. Llamar a Wompi Transfer API con netAmountCents y datos bancarios del empresario
 *      b. Crear WompiTransaction(type=TRANSFER_PAYOUT, status=PENDING)
 *      c. Actualizar Payout(status=PROCESSING, wompiTransaction=wompiTx)
 *      d. Mover netAmountCents de PAYOUTS_PENDING → [cuenta externa] (TreasuryMovement)
 *   5. El webhook de Wompi confirma cada transferencia:
 *      a. Si APPROVED: Payout(status=PAID, paidAt=NOW())
 *      b. Si DECLINED: Payout(status=FAILED) → reencolar para reintento al día siguiente
 *
 * Por qué batch y no en tiempo real:
 *   - Una venta individual puede requerir 2-3 llamadas a Wompi (cobro + transfer).
 *   - Con alto tráfico (100 ventas/hora), eso son 200-300 llamadas/hora a Wompi.
 *   - El batch agrupa todas las ventas del día en 1 sola transferencia por empresario.
 *   - Wompi cobra por transacción: el batch reduce costos operativos significativamente.
 *   - El empresario espera máximo 24h, lo cual es estándar en plataformas colombianas.
 */
@Entity
@Table(name = "payouts")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Payout {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "commercial_id", nullable = false)
    @NotNull
    private CommercialDetails commercial;

    @Column(name = "gross_amount_cents", nullable = false)
    @Positive
    @NotNull
    private Long grossAmountCents;

    @Column(name = "commission_cents", nullable = false)
    @PositiveOrZero
    @NotNull
    private Long commissionCents;

    @Column(name = "net_amount_cents", nullable = false)
    @Positive
    @NotNull
    private Long netAmountCents;

    /**
     * Snapshot del porcentaje de comisión aplicado en este payout.
     * 0 si commissionActive era false en el momento del payout.
     * Se persiste para auditoría histórica aunque cambie la tasa del plan.
     */
    @Column(name = "commission_pct_applied", nullable = false)
    @NotNull
    private Integer commissionPctApplied;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @NotNull
    private PayoutStatus status;

    /**
     * Nullable: se vincula solo cuando el job pasa el payout a PROCESSING.
     */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wompi_transaction_id")
    private WompiTransaction wompiTransaction;

    @Column(name = "scheduled_at", nullable = false)
    @NotNull
    private ZonedDateTime scheduledAt;

    /** Solo se llena cuando status = PAID. */
    @Column(name = "paid_at")
    private ZonedDateTime paidAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    @PrePersist
    public void onCreate() {
        this.createdAt = ZonedDateTime.now(ZoneOffset.UTC);
    }
}