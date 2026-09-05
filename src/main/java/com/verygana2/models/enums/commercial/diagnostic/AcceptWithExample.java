package com.verygana2.models.enums.commercial.diagnostic;

/**
 * Respuesta a las preguntas de aceptación de condiciones económicas que ofrecen
 * ver un ejemplo antes de confirmar: F-2, F-3 y F-5. NO excluye la modalidad
 * correspondiente; QUIERE_EJEMPLO no se interpreta como rechazo (ver §19.7).
 */
public enum AcceptWithExample {
    SI,
    NO,
    QUIERE_EJEMPLO
}
