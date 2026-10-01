package com.verygana2.dtos.compliance;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import com.verygana2.models.compliance.ScreeningResult;
import com.verygana2.models.enums.ScreeningList;
import com.verygana2.models.enums.ScreeningStatus;

/**
 * Vista de un ScreeningResult para el panel de cumplimiento. Reemplaza la serialización
 * directa de la entidad para no exponer ids internos de usuario/oficial.
 */
public record ScreeningResultResponseDTO(
        Long id,
        UUID userPublicId,
        String queriedName,
        String queriedDocument,
        ScreeningList restrictiveList,
        ScreeningStatus status,
        String referenceId,
        String rawResponse,
        UUID reviewedByOfficerPublicId,
        String officerNotes,
        LocalDateTime createdAt,
        LocalDateTime reviewedAt
) {
    /** {@code publicIds} debe contener la traducción de userId y reviewedByOfficerId (ver UserIdResolver#toPublicIds). */
    public static ScreeningResultResponseDTO from(ScreeningResult r, Map<Long, UUID> publicIds) {
        return new ScreeningResultResponseDTO(
                r.getId(),
                r.getUserId() != null ? publicIds.get(r.getUserId()) : null,
                r.getQueriedName(),
                r.getQueriedDocument(),
                r.getRestrictiveList(),
                r.getStatus(),
                r.getReferenceId(),
                r.getRawResponse(),
                r.getReviewedByOfficerId() != null ? publicIds.get(r.getReviewedByOfficerId()) : null,
                r.getOfficerNotes(),
                r.getCreatedAt(),
                r.getReviewedAt());
    }
}
