package com.verygana2.models.enums.commercial.diagnostic;

/** C-1: cuántas ofertas desea mantener activas. Hasta 3 favorece A; más de 3 orienta a B; no venta directa orienta a evaluar Premium. */
public enum DesiredActiveOffers {
    HASTA_TRES,
    DE_4_A_10,
    MAS_DE_10,
    NO_VENTA_DIRECTA
}
