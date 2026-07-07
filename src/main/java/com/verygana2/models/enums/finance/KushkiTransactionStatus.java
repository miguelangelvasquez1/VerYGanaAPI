package com.verygana2.models.enums.finance;

public enum KushkiTransactionStatus {

    /** Registro creado localmente antes de llamar a Kushki. */
    INITIALIZED,

    /** Kushki recibió la solicitud, procesando el ACH. */
    PENDING,

    /** Kushki confirmó la transferencia exitosamente. */
    APPROVED,

    /** El banco o Kushki rechazó la transferencia. */
    DECLINED,

    /** Error técnico en la comunicación con Kushki. */
    FAILED
}
