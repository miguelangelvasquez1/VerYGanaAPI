package com.verygana2.models.finance.plans;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import com.verygana2.models.finance.WompiTransaction;
import com.verygana2.models.finance.Wallet;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Registro inmutable de cada depósito de presupuesto publicitario.
 * Representa una entrada en el ledger del Wallet del empresario.
 *
 * Cada vez que el empresario deposita (plan inicial o recarga) se crea
 * un nuevo registro. El saldo real vive en Wallet.balanceCents.
 */
@Entity
@Table(name = "investments", indexes = {
        @Index(name = "idx_inv_wallet_id",       columnList = "wallet_id"),
        @Index(name = "idx_inv_wompi_reference", columnList = "wompi_reference")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Investment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "wallet_id", nullable = false)
    private Wallet wallet;

    /**
     * Plan que tenía el empresario en el momento de este depósito.
     * Snapshot para historial — si cambia de plan en el futuro,
     * los depósitos anteriores muestran el plan que aplicaba entonces.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_at_deposit_id", nullable = false)
    private Plan planAtDeposit;

    /**
     * Referencia Wompi guardada al crear el checkout.
     * Permite al webhook encontrar este Investment por referencia
     * sin necesitar commercial en WompiTransaction.
     *
     * Indexado para lookup O(log n) desde el webhook handler.
     */
    @Column(name = "wompi_reference", unique = true, length = 100)
    private String wompiReference;

    /**
     * WompiTransaction vinculada cuando Wompi confirma el pago.
     * Null mientras el pago está pendiente.
     */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wompi_transaction_id")
    private WompiTransaction wompiTransaction;

    /**
     * Monto depositado en centavos de COP.
     * Inmutable — representa exactamente lo que entró en este depósito.
     */
    @Column(name = "deposit_amount_cents", nullable = false, updatable = false)
    private Long depositAmountCents;

    /**
     * true cuando Wompi confirmó el pago y el saldo fue acreditado al Wallet.
     * false mientras está pendiente de confirmación.
     */
    @Column(name = "confirmed", nullable = false)
    @Builder.Default
    private Boolean confirmed = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    @Column(name = "confirmed_at")
    private ZonedDateTime confirmedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = ZonedDateTime.now(ZoneOffset.UTC);
        if (this.confirmed == null) this.confirmed = false;
    }

    /**
     * Confirma el depósito cuando Wompi aprueba el pago.
     * Llamado por PlanService.activateInvestment().
     */
    public void confirm(WompiTransaction wompiTx) {
        this.wompiTransaction = wompiTx;
        this.confirmed = true;
        this.confirmedAt = ZonedDateTime.now(ZoneOffset.UTC);
    }
}