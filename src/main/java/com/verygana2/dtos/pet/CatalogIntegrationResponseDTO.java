package com.verygana2.dtos.pet;

import com.verygana2.models.enums.CatalogRequestStatus;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

public record CatalogIntegrationResponseDTO(
        Long id,
        String companyName,
        String productName,
        String description,
        String imageUrl,
        String desiredEffects,
        CatalogRequestStatus status,
        String rejectionReason,
        /** publicId del diseñador asignado por el admin; null mientras nadie la tenga. */
        UUID assignedDesignerPublicId,
        /** Nombre del diseñador asignado, para pintarlo sin otra llamada. */
        String assignedDesignerName,
        String adminNotes,
        /** Borrador del ítem que arma el diseñador; null hasta que se acepta la solicitud. */
        Map<String, Object> itemDraft,
        Long resultCatalogItemId,
        /** Bolsa reservada para el cobro por uso, en centavos; null en solicitudes anteriores al cobro. */
        Long budgetCents,
        /** Lo que ya se cobró de la bolsa, en centavos. */
        long spentCents,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}