package com.verygana2.services.interfaces.finance;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.verygana2.dtos.prosperity.ProsperityAdjustmentRequestDTO;
import com.verygana2.dtos.prosperity.ProsperityMovementResponseDTO;
import com.verygana2.dtos.prosperity.ProsperityReconciliationResultDTO;
import com.verygana2.dtos.prosperity.ProsperityReversalRequestDTO;
import com.verygana2.dtos.prosperity.ProsperitySummaryResponseDTO;
import com.verygana2.models.finance.plans.Investment;
import com.verygana2.models.finance.prosperity.ProsperityThreshold;
import com.verygana2.models.marketplace.Purchase;
import com.verygana2.models.marketplace.PurchaseItem;

/**
 * Motor de Prosperidad (MP-05, Contrato B cláusulas 2.31-2.32, 5.7-5.8, 10.x): única
 * fuente de verdad del Umbral y del Saldo de Prosperidad de los comerciales STANDARD.
 */
public interface ProsperityService {

    /**
     * Genera el Umbral de una Inversión Computable recién confirmada: neto × multiplicador
     * del plan del depósito (feature PROSPERITY_THRESHOLD_MULTIPLIER). Idempotente por
     * inversión. Vacío si el plan no tiene multiplicador (BASIC/PREMIUM).
     */
    Optional<ProsperityThreshold> generateThreshold(Investment investment);

    /**
     * Imputa cada ítem de una compra recién aprobada contra el Saldo de su comercial
     * (absorbido = min(saldo, venta)) y fija la comisión definitiva sobre el resto.
     * Recalcula los totales de la compra. Solo absorbe si el comercial es STANDARD hoy.
     */
    void absorbPurchase(Purchase purchase);

    /** Devuelve al Saldo lo que absorbió un ítem que se reembolsa. Idempotente. */
    void reintegrateRefund(PurchaseItem item);

    /**
     * Reversión del Umbral de una inversión anulada/reembolsada (10.20). Un Umbral se
     * reversa una sola vez: un segundo intento lanza InvalidStatusException (409).
     */
    ProsperityMovementResponseDTO reverseThreshold(Long investmentId, ProsperityReversalRequestDTO request, Long adminId);

    /** Ajuste compensatorio manual con causal (10.27). */
    ProsperityMovementResponseDTO adjust(Long commercialId, ProsperityAdjustmentRequestDTO request, Long adminId);

    ProsperitySummaryResponseDTO getSummary(Long commercialId);

    Page<ProsperityMovementResponseDTO> getMovements(Long commercialId, Pageable pageable);

    /** Verifica que cada cuenta coincida con su libro. */
    ProsperityReconciliationResultDTO reconcile();
}
