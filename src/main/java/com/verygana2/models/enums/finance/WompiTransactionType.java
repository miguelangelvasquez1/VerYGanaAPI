package com.verygana2.models.enums.finance;

/**
 * Tipos de operación que VeryGana ejecuta a través de Wompi.
 */
public enum WompiTransactionType {

    /** Cobro al usuario: parte en dinero real de un copago de producto */
    CHARGE_COPAYMENT,

    /** Cobro al empresario: pago de plan básico mensual (suscripción) */
    CHARGE_PLAN_SUBSCRIPTION,

    /** Cobro al empresario: depósito inicial o recarga de plan estándar/premium */
    CHARGE_BUSINESS_DEPOSIT,

    /** Transferencia al empresario: payout de ventas acumuladas en 24h */
    TRANSFER_PAYOUT
}