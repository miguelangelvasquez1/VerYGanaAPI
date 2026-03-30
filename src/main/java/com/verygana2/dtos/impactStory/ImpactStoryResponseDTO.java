package com.verygana2.dtos.impactStory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.verygana2.models.ImpactStory.StoryStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Respuesta completa de una historia de impacto.
 * Devuelta por GET /impact-stories/{id} y en la lista paginada.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImpactStoryResponseDTO {

    private Long id;

    private String title;

    private String description;

    private LocalDate storyDate;

    private Integer beneficiariesCount;

    private BigDecimal investedAmount;

    private String investedCurrency;

    private String location;

    private String category;

    private StoryStatus status;

    private String authorName;

    /** Etiquetas separadas por coma */
    private String tags;

    /** Lista ordenada de archivos multimedia asociados a la historia */
    private List<StoryMediaResponseDTO> mediaFiles;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}