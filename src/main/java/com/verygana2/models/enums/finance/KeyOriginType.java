package com.verygana2.models.enums.finance;

/**
 * Origen tipado de un movimiento del Libro Mayor de Llaves (Contrato B 2.25:
 * "registrar origen"). Junto con {@code referenceId} identifica la operación exacta.
 */
public enum KeyOriginType {
    AD,
    GAME,
    SURVEY,
    COPAYMENT,
    PURCHASE_ITEM_REFUND,
    PET_GAME,
    EXPIRY,
    ADMIN,
    /** Movimientos anteriores al Libro Mayor (MP-12): sin secuencia ni saldos. */
    LEGACY
}
