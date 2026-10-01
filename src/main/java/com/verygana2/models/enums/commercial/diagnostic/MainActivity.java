package com.verygana2.models.enums.commercial.diagnostic;

import com.verygana2.models.enums.CommercialActivityType;

/**
 * G-5: actividad que impulsa principalmente el crecimiento de la empresa. Dato
 * clasificatorio para la ruta; no decide por sí solo. Cada opción está atada a
 * {@link CommercialActivityType} (incluidos los comodines OTROS_PRODUCTOS/OTROS_SERVICIOS,
 * en vez de una "otra actividad" ambigua) para poder autocompletar
 * CommercialDetails.commercialActivityType al enviar el diagnóstico.
 */
public enum MainActivity {
    PRODUCCION(CommercialActivityType.PRODUCTS),
    COMERCIO_PRODUCTOS(CommercialActivityType.PRODUCTS),
    DISTRIBUCION_MAYORISTA(CommercialActivityType.PRODUCTS),
    COMERCIO_MINORISTA(CommercialActivityType.PRODUCTS),
    OTROS_PRODUCTOS(CommercialActivityType.PRODUCTS),
    SERVICIOS(CommercialActivityType.SERVICES),
    RESTAURANTE(CommercialActivityType.SERVICES),
    OTROS_SERVICIOS(CommercialActivityType.SERVICES);

    private final CommercialActivityType activityType;

    MainActivity(CommercialActivityType activityType) {
        this.activityType = activityType;
    }

    public CommercialActivityType getActivityType() {
        return activityType;
    }
}
