package com.verygana2.models.enums.finance;

public enum PayoutStatus {
    /** Payout creado, esperando ejecutar la transferencia Wompi. */
    SCHEDULED,
    /** Transferencia enviada a Wompi, esperando confirmación webhook. */
    PROCESSING,
    /** Wompi confirmó la transferencia. Dinero enviado al empresario. */
    PAID,
    /** Wompi rechazó la transferencia. Se reintentará al próximo ciclo. */
    FAILED;
}
