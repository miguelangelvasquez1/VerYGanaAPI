package com.verygana2.dtos.impactStory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.verygana2.models.ImpactStory.StoryStatus;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Body del PASO 3 del flujo de publicación.
 * Se llama DESPUÉS de que todos los archivos fueron subidos exitosamente a R2.
 * Contiene los datos de la historia y la lista de mediaAssetIds ya validados.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateImpactStoryRequestDTO {

    @NotBlank(message = "El título es obligatorio")
    @Size(max = 255, message = "El título no puede superar los 255 caracteres")
    private String title;

    @NotBlank(message = "La descripción es obligatoria")
    private String description;

    @NotNull(message = "La fecha de la historia es obligatoria")
    private LocalDate storyDate;

    @NotNull(message = "El número de beneficiados es obligatorio")
    @Min(value = 0, message = "El número de beneficiados no puede ser negativo")
    private Integer beneficiariesCount;

    @DecimalMin(value = "0.0", inclusive = true, message = "El monto invertido no puede ser negativo")
    @Digits(integer = 13, fraction = 2, message = "Formato de monto inválido")
    private BigDecimal investedAmount;

    @Size(max = 10, message = "El código de moneda no puede superar los 10 caracteres")
    private String investedCurrency;

    @Size(max = 100, message = "La ubicación no puede superar los 100 caracteres")
    private String location;

    @Size(max = 100, message = "La categoría no puede superar los 100 caracteres")
    private String category;

    @NotNull(message = "El estado es obligatorio")
    private StoryStatus status;

    @Size(max = 150, message = "El nombre del autor no puede superar los 150 caracteres")
    private String authorName;

    /** Etiquetas separadas por coma (ej. "agua,comunidad,2024") */
    @Size(max = 500, message = "Las etiquetas no pueden superar los 500 caracteres")
    private String tags;

    /** Lista de archivos multimedia ya subidos a R2. Puede estar vacía. */
    @Valid
    private List<CreateStoryMediaRequestDTO> mediaFiles;
}
