package com.verygana2.models.enums.declarations;

/**
 * Situaciones distintas que no se confunden: la falta de manifestación da lugar a aclaración;
 * una falla técnica no es una negativa; solo una negativa expresa se atribuye al empresario.
 */
public enum ManifestationStatus {
    MANIFESTED,
    NOT_MANIFESTED,
    EXPRESS_REFUSAL,
    TECHNICAL_INCIDENT
}
