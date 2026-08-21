package com.verygana2.models.enums.finance.plans;

/**
 * Ciclo de vida de una solicitud explícita de cambio de plan. Delega la
 * revisión/firma por completo al {@link com.verygana2.models.commercial.CommercialContract}
 * vinculado — no reimplementa los estados finos de ContractStatus.
 */
public enum PlanChangeRequestStatus {
    /** Creada, contrato aún no generado. */
    REQUESTED,
    /** Contrato generado; revisión de negocio/VerYGana + firma en curso. */
    CONTRACT_PENDING_REVIEW,
    /** Firmado; si requiere abono adicional, queda bloqueado en el pago. */
    CONTRACT_SIGNED,
    /** Checkout de abono generado, esperando confirmación de Wompi. */
    PAYMENT_PENDING,
    /** Terminal — commercial.currentPlan cambió de verdad. */
    APPLIED,
    /** Terminal — contrato rechazado por VerYGana. */
    REJECTED,
    /** Terminal — el commercial retiró la solicitud antes de revisión. */
    CANCELLED
}
