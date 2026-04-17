package com.verygana2.models.enums.finance;

/**
 * Estados posibles de una transacción según Wompi.
 * Mapean 1-a-1 con los estados que Wompi envía en sus webhooks.
 * Referencia: https://docs.wompi.co/docs/colombia/estado-de-una-transaccion
 */
public enum WompiTransactionStatus {

    /** La transacción fue creada pero Wompi aún no la procesó */
    PENDING,

    /** Wompi confirmó el cobro o transferencia exitosamente */
    APPROVED,

    /** El banco o Wompi rechazó la transacción */
    DECLINED,

    /** Ocurrió un error técnico en el procesamiento */
    ERROR,

    /** La transacción fue anulada (solo aplica dentro del día hábil) */
    VOIDED
}