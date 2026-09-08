package com.verygana2.models.enums.pqrs;

public enum PqrsStatus {
    PENDIENTE_ASIGNACION,
    RECIBIDA,
    EN_REVISION,
    /**
     * Solo para PQRS de marketplace con action=REFUND cuyo reembolso tiene
     * porción en efectivo: el admin ya decidió reembolsar pero el pago manual
     * todavía no se confirma (ver CashRefundServiceImpl.markPaid). El PQRS
     * pasa a RESUELTA recién cuando ese pago se marca como PAID.
     */
    PENDIENTE_PAGO_REEMBOLSO,
    RESUELTA,
    CERRADA
}
