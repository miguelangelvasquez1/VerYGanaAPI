package com.verygana2.models.enums.marketplace;

/**
 * Un componente reconocido nunca se edita ni se borra: su anulación es otro registro
 * REVERSED que apunta al original.
 */
public enum SaleComponentStatus {
    RECOGNIZED,
    REVERSED
}
