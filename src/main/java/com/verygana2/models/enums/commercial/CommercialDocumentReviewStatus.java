package com.verygana2.models.enums.commercial;

/**
 * Revisión del contenido de un documento por compliance. Va aparte de
 * {@link CommercialDocumentStatus}, que solo dice si el archivo llegó bien a storage.
 */
public enum CommercialDocumentReviewStatus {
    NOT_REVIEWED,
    /** Titular, alcance y vigencia comprobados. */
    ACCEPTED,
    /** Tiene observaciones que el empresario puede aclarar o subsanar. */
    OBSERVED,
    /** No sirve para lo que se aportó (p. ej. de otro titular). */
    REJECTED
}
