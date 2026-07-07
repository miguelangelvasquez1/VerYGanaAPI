package com.verygana2.dtos.pet;

import com.verygana2.models.enums.CatalogRequestStatus;

import java.time.LocalDateTime;

public record CatalogIntegrationResponseDTO(
        Long id,
        String companyName,
        String productName,
        String description,
        String imageUrl,
        String desiredEffects,
        CatalogRequestStatus status,
        String rejectionReason,
        Long resultCatalogItemId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}