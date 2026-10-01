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
    BASIC_PLAN_SUBSCRIPTION,

    /**
     * El multiplicador de nivel emitió MENOS llaves de las que el anunciante
     * financió. El respaldo sobrante en KEYS_RESERVE deja de tener pasivo
     * detrás y se reconoce como ingreso de OPERATIONS.
     */
    KEYS_ISSUANCE_SURPLUS_TO_OPERATIONS,

    /**
     * El multiplicador de nivel emitió MÁS llaves de las que el anunciante
     * financió (multiplicador > 1). OPERATIONS financia el exceso para que
     * cada llave emitida siga teniendo respaldo en KEYS_RESERVE.
     */
    KEYS_ISSUANCE_DEFICIT_FUNDING,

    /**
     * Llaves gastadas en el juego de mascotas. El consumidor las consume, así que
     * dejan de ser pasivo y su respaldo se reconoce como ingreso de OPERATIONS.
     *
     * NO va a PAYOUTS_PENDING: aunque el ítem venga de la solicitud de integración
     * de un comercial, hoy PayoutItem solo se construye desde Copayment y esas
     * ventas nunca generan un pago. Mandar la plata a la sala de espera de payouts
     * crearía una obligación que nada liquida.
     */
    PET_GAME_KEYS_TO_OPERATIONS,

    /**
     * Cobro por uso de un ítem de mascotas: cada compra del ítem descuenta el cobro de
     * la bolsa que el comercial reservó al pedir la integración. Esa bolsa salió de su
     * wallet, cuyo respaldo está en KEYS_RESERVE; como no se emite ninguna llave por
     * ella, el respaldo pasa a OPERATIONS como ingreso de la plataforma.
     */
    PET_ITEM_CHARGE_TO_OPERATIONS,

    /** Reembolso de un PurchaseItem: revierte la comisión retenida (OPERATIONS → PAYOUTS_PENDING) */
    COMMISSION_REVERSAL,

    /**
     * Reembolso de un PurchaseItem: la porción en llaves vuelve a KEYS_RESERVE
     * (PAYOUTS_PENDING → KEYS_RESERVE) — repone el fondo que respalda las
     * llaves que se le acreditan de vuelta al comprador (ver KeyTransaction.CREDIT_COPAYMENT_REFUND).
     */
    REFUND_KEYS_TO_RESERVE,

    /**
     * Reembolso de un PurchaseItem: la porción en efectivo sale de
     * PAYOUTS_PENDING hacia OPERATIONS — queda ahí como pasivo pendiente de
     * que el admin haga la transferencia manual (ver PurchaseItemCashRefund).
     */
    REFUND_CASH_TO_OPERATIONS,

    /** Reembolso en efectivo pagado manualmente por el admin: sale de OPERATIONS hacia afuera del sistema */
    REFUND_TO_BUYER,

    /** IVA (19%) sobre un depósito de inversión STANDARD/PREMIUM → TAX_RESERVE */
    BUSINESS_DEPOSIT_VAT,

    /** IVA (19%) sobre una suscripción mensual BASIC → TAX_RESERVE */
    BASIC_PLAN_SUBSCRIPTION_VAT,

    /** Porción de IVA (19%) de una comisión de venta retenida → TAX_RESERVE (la comisión ya incluye IVA) */
    COMMISSION_VAT_RETENTION,

    /** Reembolso de un PurchaseItem: revierte la porción de IVA de la comisión (TAX_RESERVE → PAYOUTS_PENDING) */
    COMMISSION_VAT_REVERSAL
}
