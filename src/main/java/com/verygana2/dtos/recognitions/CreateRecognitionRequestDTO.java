package com.verygana2.dtos.recognitions;

import java.time.ZonedDateTime;
import java.util.UUID;

import com.verygana2.models.enums.recognitions.RecognitionCriterion;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Reconocimiento que crea un admin: a quién, qué producto y por qué. El criterio y su valor
 * vienen de la lista de candidatos (GET /admin/recognitions/candidates) y quedan guardados
 * como soporte de la elección.
 */
public record CreateRecognitionRequestDTO(
        @NotNull UUID beneficiaryPublicId,
        @NotNull Long productId,
        @NotNull @Min(1) @Max(10) Integer quantity,
        @NotNull RecognitionCriterion criterion,
        Long metricValue,
        ZonedDateTime periodFrom,
        ZonedDateTime periodTo,
        @NotBlank @Size(max = 500) String reason) {
}
