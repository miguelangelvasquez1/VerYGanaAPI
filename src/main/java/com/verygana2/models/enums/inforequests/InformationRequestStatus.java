package com.verygana2.models.enums.inforequests;

/** El cierre sale solo de una revisión: recibir una respuesta no cierra el requerimiento. */
public enum InformationRequestStatus {
    OPEN,
    RESPONDED,
    /** Revisada con observaciones: el empresario puede responder otra vez. */
    OBSERVED,
    CLOSED_SATISFIED,
    CLOSED_WITH_FINDING,
    CANCELLED;

    public boolean isOpen() {
        return this == OPEN || this == RESPONDED || this == OBSERVED;
    }
}
