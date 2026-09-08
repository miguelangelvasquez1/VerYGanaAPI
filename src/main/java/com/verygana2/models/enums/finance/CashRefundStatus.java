package com.verygana2.models.enums.finance;

public enum CashRefundStatus {
    /** Esperando datos bancarios del comprador y/o la transferencia manual del admin. */
    PENDING_PAYMENT,
    /** El admin confirmó que ya hizo la transferencia manual. */
    PAID
}
