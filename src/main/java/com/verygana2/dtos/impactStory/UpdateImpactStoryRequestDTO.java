package com.verygana2.dtos.impactStory;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.verygana2.models.ImpactStory.StoryStatus;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Body para actualizar una historia de impacto existente.
 * Todos los campos son opcionales: solo los no-null se aplican al entity.
 * La gestión de archivos multimedia en edición se maneja por separado
 * a través del endpoint de media assets.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateImpactStoryRequestDTO {

    @Size(max = 255, message = "El título no puede superar los 255 caracteres")
    private String title;

    private String description;

    private LocalDate storyDate;

    @Min(value = 0, message = "El número de beneficiados no puede ser negativo")
    private Integer beneficiariesCount;

    @DecimalMin(value = "0.0", inclusive = true, message = "El monto invertido no puede ser negativo")
    @Digits(integer = 13, fraction = 2, message = "Formato de monto inválido")
    private BigDecimal investedAmount;

    @Size(max = 10)
    private String investedCurrency;

    @Size(max = 100)
    private String location;

    @Size(max = 100)
    private String category;

    private StoryStatus status;

    @Size(max = 150)
    private String authorName;

    @Size(max = 500)
    private String tags;
}