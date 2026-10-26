package com.verygana2.dtos.recognitions;

import java.time.ZonedDateTime;
import java.util.UUID;

import com.verygana2.models.enums.recognitions.RecognitionStatus;

public record RecognitionSummaryDTO(
        UUID publicId,
        RecognitionStatus status,
        UUID beneficiaryPublicId,
        String beneficiaryName,
        String productName,
        String commercialName,
        int quantity,
        long totalCents,
        ZonedDateTime createdAt) {
}
