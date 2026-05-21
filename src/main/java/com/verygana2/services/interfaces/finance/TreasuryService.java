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
