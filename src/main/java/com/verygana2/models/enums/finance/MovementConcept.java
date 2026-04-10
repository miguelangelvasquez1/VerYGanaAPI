package com.verygana2.models.enums.finance;

/**
 * Categorías de movimientos en el libro de tesorería.
 * Cada valor describe qué operación de negocio originó el movimiento,
 * lo que permite generar reportes contables por categoría.
 */
public enum MovementConcept {

    /** Ingreso de un depósito empresarial — distribución al 60% de llaves */
    BUSINESS_DEPOSIT_KEYS,

    /** Ingreso de un depósito empresarial — distribución al 10% de fortalecimiento */
    BUSINESS_DEPOSIT_FORTIFICATION,

    /** Ingreso de un depósito empresarial — distribución al 30% de operación */
    BUSINESS_DEPOSIT_OPERATIONS,

    /** La app asume la parte de llaves de un copago (sale de KEYS_RESERVE) */
    COPAYMENT_KEYS_CONVERSION,

    /** Dinero acumulado de ventas que pasa a sala de espera para payout diario */
    SALE_TO_PAYOUT_PENDING,

    /** Payout enviado al empresario vía Wompi (sale de PAYOUTS_PENDING) */
    PAYOUT_TO_BUSINESS,

    /** Comisión de venta retenida hacia OPERATIONS */
    COMMISSION_RETENTION,

    /** Llaves vencidas convertidas a dinero para el fondo de fortalecimiento */
    EXPIRED_KEYS_TO_FORTIFICATION,

    /** Compra a empresario débil realizada con el fondo de fortalecimiento */
    FORTIFICATION_PURCHASE,

    /** Plan básico mensual cobrado — distribución a operaciones */
    BASIC_PLAN_SUBSCRIPTION
} 
