package com.verygana2.dtos.user.commercial.expediente;

import java.time.LocalDate;
import java.time.ZonedDateTime;

import com.verygana2.models.enums.commercial.CommercialDocumentReviewStatus;
import com.verygana2.models.enums.commercial.CommercialDocumentStatus;
import com.verygana2.models.enums.commercial.CommercialDocumentType;

/**
 * Documento del expediente. {@code status} VALIDATED = vigente; SUPERSEDED = reemplazado
 * (historial). {@code expired} solo es true si el documento tiene {@code expiresAt}.
 */
public record ExpedienteDocumentDTO(
        Long id,
        CommercialDocumentType documentType,
        String originalFileName,
        Long sizeBytes,
        CommercialDocumentStatus status,
        ZonedDateTime uploadedAt,
        Long replacesDocumentId,
        ZonedDateTime supersededAt,
        CommercialDocumentReviewStatus reviewStatus,
        ZonedDateTime reviewedAt,
        String reviewNotes,
        String holderIdentification,
        LocalDate issuedAt,
        LocalDate expiresAt,
        boolean expired,
        String downloadUrl) {
}
