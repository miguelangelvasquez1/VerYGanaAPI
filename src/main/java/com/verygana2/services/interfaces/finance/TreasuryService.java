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

    /**
     * @param baseAmountCents monto de inversión sin IVA — se reparte 60/10/30
     *                        entre KEYS_RESERVE/FORTIFICATION/OPERATIONS.
     * @param vatAmountCents  IVA adicional cobrado sobre el depósito (puede ser
     *                        0) — va completo a TAX_RESERVE, no se mezcla con
     *                        la distribución 60/10/30.
     */
    void distributeDeposit(Long baseAmountCents, Long vatAmountCents, CommercialDetails commercial, UUID referenceId);

    /**
     * @param baseAmountCents precio de la suscripción sin IVA — va completo a OPERATIONS.
     * @param vatAmountCents  IVA adicional cobrado sobre la suscripción (puede ser 0) — va a TAX_RESERVE.
     */
    void distributeSubscription(Long baseAmountCents, Long vatAmountCents, CommercialDetails commercial,
            UUID referenceId);
    void convertKeysToPayoutPending(Long amountCents, UUID referenceId);
    void moveCashToPayoutPending(Long amountCents, UUID referenceId);

    /**
     * @param amountCents comisión total retenida (ya incluye IVA)
     * @param vatCents    porción de esa comisión correspondiente a IVA (puede ser 0) — se
     *                    extrae y va a TAX_RESERVE; el resto (amountCents - vatCents) va a OPERATIONS.
     */
    void retainCommission(Long amountCents, Long vatCents, UUID referenceId, String referenceType);
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
     * La porción de IVA de la comisión (si la hubo) también se revierte:
     * TAX_RESERVE → PAYOUTS_PENDING.
     *
     * @param commissionCents    comisión retenida sobre este ítem (puede ser 0), incluye IVA
     * @param commissionVatCents porción de esa comisión que fue a TAX_RESERVE (puede ser 0)
     * @param keysPortionCents   porción del precio del ítem pagada con llaves (puede ser 0)
     * @param cashPortionCents   porción del precio del ítem pagada en efectivo (puede ser 0)
     * @param referenceId        id del Copayment original, para trazabilidad
     */
    void reversePurchaseItemForRefund(Long commissionCents, Long commissionVatCents, Long keysPortionCents,
            Long cashPortionCents, UUID referenceId);

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
