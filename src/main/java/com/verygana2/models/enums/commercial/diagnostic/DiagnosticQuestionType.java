package com.verygana2.models.enums.commercial.diagnostic;

/** Tipo de control con el que el front debe renderizar una pregunta del diagnóstico. */
public enum DiagnosticQuestionType {
    /** Una sola opción entre {@code options}. */
    SINGLE_CHOICE,
    /** Varias opciones de {@code options}; respeta {@code maxSelections} y, si {@code ordered}, el orden = prioridad. */
    MULTI_CHOICE,
    /** Sí / No — se envía como boolean. {@code options} trae las etiquetas "true"/"false". */
    BOOLEAN
}
