package com.verygana2.models.enums.inforequests;

/** Evento que justifica pedir información (4.8). Cada uno se trata según su naturaleza. */
public enum InformationRequestTrigger {
    DOCUMENT_EXPIRED,
    ACTIVITY_CHANGE,
    ESTABLISHMENT_CHANGE,
    MATERIAL_OPERATION_INCREASE,
    NEW_REQUIREMENT,
    INCONSISTENCY,
    INCIDENT,
    RISK,
    /** La casilla del compromiso de formalización no llegó marcada: da lugar a aclaración. */
    MISSING_MANIFESTATION,
    /** Negativa expresa al compromiso: se verifica antes de remitir a cualquier procedimiento. */
    EXPRESS_REFUSAL
}
