package com.verygana2.dtos.pet;

import com.verygana2.models.enums.CatalogRequestStatus;

import java.time.LocalDateTime;
import java.util.Map;

public record CatalogIntegrationResponseDTO(
        Long id,
        String companyName,
        String productName,
        String description,
        String imageUrl,
        String desiredEffects,
        CatalogRequestStatus status,
        String rejectionReason,
        /** userId del diseñador asignado por el admin; null mientras nadie la tenga. */
        Long assignedDesignerUserId,
        /** Nombre del diseñador asignado, para pintarlo sin otra llamada. */
        String assignedDesignerName,
        String adminNotes,
        /** Borrador del ítem que arma el diseñador; null hasta que se acepta la solicitud. */
        Map<String, Object> itemDraft,
        Long resultCatalogItemId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}