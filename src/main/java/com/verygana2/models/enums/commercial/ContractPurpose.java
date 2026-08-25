package com.verygana2.models.enums.commercial;

/** Qué evento originó un {@link com.verygana2.models.commercial.CommercialContract}. */
public enum ContractPurpose {
    /** Contrato Marco único generado durante el onboarding (pasos 9-11). */
    ONBOARDING,
    /** Recarga de saldo STANDARD/PREMIUM — específico a un monto, firma antes de pagar. */
    RECHARGE,
    /** Cambio explícito a otro plan — siempre requiere aprobación admin. */
    PLAN_CHANGE
}
