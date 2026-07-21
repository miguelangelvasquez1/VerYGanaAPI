package com.verygana2.models.enums.commercial;

/** Ruta de clasificación automática asignada al comerciante tras el diagnóstico. */
public enum CommercialRoute {
    A, // Comercial por resultado — sin tarifa fija (o mínima), vende, comisión sugerida 20% sobre venta
    B, // Comercial con pauta y gamificación — tarifa fija media/alta, vende, comisión sugerida 10% sobre venta
    C, // Gran marca de visibilidad — tarifa fija alta, normalmente no vende, comisión especial o no aplica
    D, // Proveedor o aliado de servicios — requiere integración técnica, comisión negociada
    E  // Piloto, fundador o alianza especial — requiere negociación corporativa o aprobación previa especial
}
