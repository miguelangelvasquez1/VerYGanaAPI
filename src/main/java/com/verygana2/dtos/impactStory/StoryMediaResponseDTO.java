package com.verygana2.dtos.impactStory;

import java.time.LocalDateTime;

import com.verygana2.models.enums.MediaType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Representación de un archivo multimedia dentro de una historia de impacto.
 * Incluido en ImpactStoryResponseDTO.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StoryMediaResponseDTO {

    private Long id;

    private MediaType mediaType;

    private String publicUrl;

    private String sizeBytes;

    private Long fileSize;

    private String mimeType;

    /** URL del thumbnail (disponible para videos y opcionalmente para imágenes) */
    private String thumbnailUrl;

    private String altText;

    private Integer displayOrder;

    private Boolean isCover;

    private LocalDateTime uploadedAt;
}
