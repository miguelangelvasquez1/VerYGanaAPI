package com.verygana2.dtos.impactStory;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Referencia a un archivo multimedia ya subido al CDN.
 * Forma parte del body del PASO 3 (CreateImpactStoryRequestDTO).
 *
 * El backend usa el mediaAssetId para localizar el StoryMediaAsset
 * correspondiente y validar que está en estado PENDING.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateStoryMediaRequestDTO {

    /**
     * ID del StoryMediaAsset devuelto en el PASO 1.
     * El backend lo valida antes de asociarlo a la historia.
     */
    @NotBlank(message = "El mediaAssetId es obligatorio")
    private String mediaAssetId;

    /** Nombre original del archivo (ej. "foto_comunidad.jpg") */
    @NotBlank(message = "El nombre del archivo es obligatorio")
    private String fileName;

    /** Texto alternativo para accesibilidad */
    private String altText;

    /**
     * Posición en la galería (0-based).
     * Si es null, el backend asignará el índice del array.
     */
    @PositiveOrZero(message = "El orden debe ser mayor o igual a 0")
    private Integer displayOrder;

    /** Si es true, este archivo se usará como imagen de portada de la historia */
    @NotNull(message = "isCover es obligatorio")
    private Boolean isCover;
}