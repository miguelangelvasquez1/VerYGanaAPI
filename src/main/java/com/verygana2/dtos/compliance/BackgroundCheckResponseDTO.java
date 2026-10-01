package com.verygana2.dtos.compliance;

import java.time.ZonedDateTime;
import java.util.UUID;

import com.verygana2.models.compliance.BackgroundCheck;
import com.verygana2.models.enums.BackgroundCheckStatus;
import com.verygana2.models.enums.BackgroundCheckType;

/** Vista de un BackgroundCheck para el panel de cumplimiento (sin ids internos de usuario). */
public record BackgroundCheckResponseDTO(
        Long id,
        Long contractId,
        String checkId,
        BackgroundCheckType checkType,
        String country,
        String subjectName,
        String subjectDocument,
        BackgroundCheckStatus status,
        Double score,
        String pdfReportUrl,
        UUID requestedByOfficerPublicId,
        ZonedDateTime requestedAt,
        ZonedDateTime completedAt
) {
    public static BackgroundCheckResponseDTO from(BackgroundCheck c, UUID requestedByOfficerPublicId) {
        return new BackgroundCheckResponseDTO(
                c.getId(),
                c.getContractId(),
                c.getCheckId(),
                c.getCheckType(),
                c.getCountry(),
                c.getSubjectName(),
                c.getSubjectDocument(),
                c.getStatus(),
                c.getScore(),
                c.getPdfReportUrl(),
                requestedByOfficerPublicId,
                c.getRequestedAt(),
                c.getCompletedAt());
    }
}
