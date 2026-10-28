package com.verygana2.models.enums.marketplace;

/**
 * Estado de la Venta Computable de un ítem (Contrato B 5.9). Una orden pendiente nunca es
 * venta: solo pasa a RECOGNIZED cuando los componentes que financian el precio suman
 * exactamente su subtotal. REVERSED conserva el registro original de una venta anulada.
 */
public enum ComputableSaleStatus {
    PENDING,
    RECOGNIZED,
    REVERSED
}
