package com.verygana2.dtos.game.campaign;

import java.math.BigDecimal;
import java.util.List;

import com.verygana2.models.enums.TargetGender;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateCampaignRequestDTO {
    
    @NotEmpty(message = "Se requieren assets")
    private List<@NotNull Long> assetIds;

    @NotNull(message = "Presupuesto requerido")
    @Min(value = 10, message = "Presupuesto mínimo: $10")
    private BigDecimal budget;

    private String targetUrl; // Opcional

    @NotEmpty(message = "Se requiere al menos una categoría")
    private List<@NotNull Long> categoryIds;

    @NotNull(message = "Edad mínima requerida")
    @Min(value = 13, message = "Edad mínima: 13")
    @Max(value = 100, message = "Edad máxima: 100")
    private Integer minAge;

    @NotNull(message = "Edad máxima requerida")
    @Min(value = 13, message = "Edad mínima: 13")
    @Max(value = 100, message = "Edad máxima: 100")
    private Integer maxAge;

    @NotNull(message = "Género objetivo requerido")
    private TargetGender targetGender; // ALL, MALE, FEMALE

    private List<String> targetMunicipalityCodes; // Opcional, puede ser vacío
}