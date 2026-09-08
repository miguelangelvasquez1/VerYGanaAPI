package com.verygana2.services.interfaces.finance;

import java.util.UUID;

import com.verygana2.models.enums.finance.PayoutStatus;

/**
 * Ejecuta la transferencia Wompi de un único Payout en su propia transacción
 * (REQUIRES_NEW), aislada del resto del batch que lo dispara.
 *
 * Antes, {@code processScheduledPayouts()}/{@code retryFailedPayouts()} corrían
 * los ~100 payouts del día dentro de UNA sola transacción a nivel de método: si
 * algo corrompía la sesión de Hibernate a mitad de camino (ej. una
 * DataIntegrityViolationException en el payout #47), toda la transacción podía
 * quedar rollback-only y revertir en BD los payouts #1-#46 aunque el dinero ya
 * hubiera salido por Wompi. Al aislar cada payout en su propia transacción, un
 * fallo — incluso uno que corrompa la sesión — solo afecta a ese payout.
 */
public interface PayoutExecutionService {

    /**
     * Envía a Wompi el Payout SCHEDULED indicado y actualiza su estado.
     * Devuelve el estado resultante (PROCESSING/FAILED) para que el batch
     * orquestador (ver {@link com.verygana2.services.finance.PayoutServiceImpl})
     * pueda llevar un balance corriendo de la cuenta de dispersión sin volver
     * a consultar el payout.
     */
    PayoutStatus executeScheduledPayout(UUID payoutId);

    /**
     * Igual que {@link #executeScheduledPayout}, pero para un reintento:
     * incrementa {@code retryCount} y limpia el motivo de fallo previo antes
     * de reenviar. Devuelve EXHAUSTED sin llamar a Wompi si ya se alcanzó el
     * máximo de reintentos.
     */
    PayoutStatus executeRetry(UUID payoutId);

    /**
     * Marca el Payout como FAILED sin llamar a Wompi, porque el batch
     * orquestador ya determinó (con su balance corriendo local) que el saldo
     * de la cuenta de dispersión no alcanza para esta transferencia. Evita
     * gastar la llamada a Wompi en un rechazo predecible y deja un motivo
     * explícito en vez del genérico que devolvería Wompi.
     */
    void markInsufficientBalance(UUID payoutId, long neededCents, long availableCents);
}
