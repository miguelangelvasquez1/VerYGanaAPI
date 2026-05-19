package com.verygana2.models.enums.finance.plans;

/**
 * Estado del ciclo de vida de una suscripción de plan básico.
 */
public enum SubscriptionStatus {

    /**
     * Checkout generado pero Wompi aún no confirmó el pago.
     * La Subscription existe en BD pero el período aún no ha comenzado.
     * Si Wompi rechaza o el empresario abandona, queda en este estado
     * y el job de limpieza puede eliminarla después de X horas.
     */
    PENDING_PAYMENT,

    /**
     * Período activo y vigente. Wompi confirmó el pago.
     * startDate y endDate están definidos.
     */
    ACTIVE,

    /**
     * El período venció sin renovación.
     * El job diario pasa ACTIVE → EXPIRED cuando endDate < now.
     */
    EXPIRED,

    /**
     * El empresario renovó antes del vencimiento.
     * La suscripción anterior pasa a RENEWED cuando se activa la nueva.
     */
    RENEWED,

    /**
     * Cancelado manualmente. El acceso se mantiene hasta endDate.
     */
    CANCELLED,

    /**
     * Pago rechazado por Wompi.
     * Distinto de PENDING_PAYMENT — aquí Wompi respondió explícitamente
     * con DECLINED o ERROR.
     */
    PAYMENT_FAILED
}