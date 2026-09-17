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

    /**
     * Reconcilia lo que el anunciante financió contra lo que realmente se emitió
     * al consumidor, cuando el multiplicador de nivel hace que no coincidan.
     *
     * La parte financiada ya está en KEYS_RESERVE desde distributeDeposit(), así
     * que aquí solo se mueve la DIFERENCIA:
     *   financiado > emitido → KEYS_RESERVE → OPERATIONS (sobrante sin pasivo)
     *   financiado < emitido → OPERATIONS → KEYS_RESERVE (respaldar el exceso)
     *   iguales              → no-op, sin movimiento en el libro
     *
     * @param fundedCents   lo que el anunciante pagó por la interacción (base)
     * @param issuedCents   lo que se acreditó en la billetera del consumidor
     * @param referenceId   ID de la interacción (watch session, survey session)
     * @param referenceType "AD_LIKE", "SURVEY_REWARD", ...
     */
    void settleKeyIssuance(long fundedCents, long issuedCents, UUID referenceId, String referenceType);

    /**
     * Registra el consumo de llaves en el juego de mascotas: KEYS_RESERVE → OPERATIONS.
     *
     * El usuario gastó sus llaves, así que el pasivo baja; sin este movimiento el
     * respaldo se quedaría en KEYS_RESERVE sin nada detrás.
     *
     * @param amountCents valor en centavos de las llaves consumidas
     * @param referenceId id de la KeyTransaction del gasto
     */
    void registerPetGameSpend(long amountCents, UUID referenceId);

    TreasurySnapshot getSnapshot();

    /** Balance enriquecido con estado de umbrales para el endpoint de auditoría. */
    TreasuryBalanceResponseDTO getBalanceReport();

    /** Historial paginado de movimientos para una cuenta específica. */
    Page<TreasuryMovementResponseDTO> getMovements(TreasuryAccountCode code, Pageable pageable);

    /** Verifica integridad de saldos: ninguna cuenta puede tener saldo negativo. */
    void runReconciliation();
}
