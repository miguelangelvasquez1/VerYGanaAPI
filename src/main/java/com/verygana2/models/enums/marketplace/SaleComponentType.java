package com.verygana2.models.enums.marketplace;

/**
 * Componentes económicos que completan el precio reconocido de un ítem (Contrato B 5.9).
 * Patrocinios, aportes Premium o de terceros y bonos se agregan cuando existan esas
 * funciones: no hay un tipo genérico "otros" para no ocultar duplicaciones.
 */
public enum SaleComponentType {
    /** Dinero pagado por el usuario (copago vía Wompi). */
    USER_CASH,
    /** Respaldo económico de las llaves usadas, que paga KEYS_RESERVE. */
    KEYS_BACKING,
    /** Pago de VERYGANA como compradora, con cargo al fondo de fortalecimiento. */
    VERYGANA_PAYMENT
}
