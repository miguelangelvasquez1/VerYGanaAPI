package com.verygana2.models.enums.commercial;

/** Ruta de clasificación automática asignada al comerciante tras el diagnóstico. */
public enum CommercialRoute {
    A, // Tarifa fija, presencia en espacios generales — onboarding simple
    B, // Comisión por venta, sin requisitos especiales — modelo estándar
    C, // Requiere integración técnica (API/conciliación/activación) o juegos personalizados
    D, // Pertenece a un sector regulado — requiere validación de cumplimiento adicional
    E  // Requiere negociación corporativa o aprobación previa especial
}
