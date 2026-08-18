package com.verygana2.services.interfaces.finance;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.verygana2.dtos.treasury.TreasuryBalanceResponseDTO;
import com.verygana2.dtos.treasury.TreasuryMovementResponseDTO;
import com.verygana2.models.enums.finance.TreasuryAccountCode;
import com.verygana2.models.records.TreasurySnapshot;
import com.verygana2.models.userDetails.CommercialDetails;

public interface TreasuryService {
    void distributeDeposit(Long amountCents, CommercialDetails commercial, UUID referenceId);
    void distributeSubscription(Long amountCents, CommercialDetails commercial, UUID referenceId);
    void convertKeysToPayoutPending(Long amountCents, UUID referenceId);
    void moveCashToPayoutPending(Long amountCents, UUID referenceId);
    void retainCommission(Long amountCents, UUID referenceId, String referenceType);
    void registerPayoutSent(Long amountCents, UUID referenceId);

    /**
     * Reversa internamente un PurchaseItem reembolsado: la comisión retenida
     * vuelve de OPERATIONS a PAYOUTS_PENDING; la porción que se pagó con
     * llaves vuelve a KEYS_RESERVE (repone el fondo, ver
     * KeyTransaction.CREDIT_COPAYMENT_REFUND); y la porción en efectivo sale
     * de PAYOUTS_PENDING hacia OPERATIONS, donde queda como pasivo pendiente
     * de que el admin haga la transferencia manual al comprador (ver
     * PurchaseItemCashRefund) — Wompi no documenta un endpoint de reverso de
     * cargos, así que ese pago no se automatiza.
     *
     * @param commissionCents  comisión retenida sobre este ítem (puede ser 0)
     * @param keysPortionCents porción del precio del ítem pagada con llaves (puede ser 0)
     * @param cashPortionCents porción del precio del ítem pagada en efectivo (puede ser 0)
     * @param referenceId      id del Copayment original, para trazabilidad
     */
    void reversePurchaseItemForRefund(Long commissionCents, Long keysPortionCents, Long cashPortionCents,
            UUID referenceId);

    /**
     * Registra que el admin ya hizo la transferencia manual de un reembolso
     * en efectivo: el dinero sale de OPERATIONS hacia afuera del sistema.
     *
     * @param amountCents monto transferido manualmente
     * @param referenceId id del PurchaseItemCashRefund
     */
    void registerManualCashRefundPaid(Long amountCents, UUID referenceId);

    /**
     * Mueve el valor en COP de las llaves vencidas de KEYS_RESERVE → FORTIFICATION.
     * Llamado por el job nocturno de vencimientos.
     *
     * @param amountCents totalExpiredKeys × KEY_VALUE_CENTS
     * @param batchId     UUID del lote de vencimiento para trazabilidad
     */
    void moveExpiredKeysToFortification(Long amountCents, UUID batchId);

    TreasurySnapshot getSnapshot();

    /** Balance enriquecido con estado de umbrales para el endpoint de auditoría. */
    TreasuryBalanceResponseDTO getBalanceReport();

    /** Historial paginado de movimientos para una cuenta específica. */
    Page<TreasuryMovementResponseDTO> getMovements(TreasuryAccountCode code, Pageable pageable);

    /** Verifica integridad de saldos: ninguna cuenta puede tener saldo negativo. */
    void runReconciliation();
}
