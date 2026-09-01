package com.verygana2.models.enums.commercial.diagnostic;

/**
 * Respuesta a preguntas que admiten pedir una explicación: F-6 (régimen de
 * Prosperidad), PR-5 (enfoque de marca Premium) y PR-6 (métricas y protección de
 * datos). NECESITA_EXPLICACION no equivale a rechazo.
 */
public enum Understanding {
    SI,
    NO,
    NECESITA_EXPLICACION
}
