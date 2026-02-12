package com.verygana2.dtos.game.campaign;

import java.math.BigDecimal;
import java.util.List;

import com.verygana2.dtos.game.GameConfigDTO;
import com.verygana2.models.enums.TargetGender;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
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
    

    @NotNull(message = "Valor de moneda requerido")
    @DecimalMin(value = "0.01", message = "Valor mínimo por moneda: $0.01")
    @DecimalMax(value = "100.00", message = "Valor máximo por moneda: $100.00")
    private BigDecimal coinValue;

    @NotNull(message = "Monedas de completación requeridas")
    @Min(value = 0, message = "Las monedas de completación no pueden ser negativas")
    private Integer completionCoins;

    @NotNull(message = "Presupuesto en monedas requerido")
    @Min(value = 1, message = "El presupuesto en monedas debe ser mayor a 0")
    private Integer budgetCoins;

    @NotNull(message = "Máximo de monedas por sesión requerido")
    @Min(value = 1, message = "El máximo de monedas por sesión debe ser mayor a 0")
    private Integer maxCoinsPerSession;

    @NotNull(message = "Máximo de sesiones por usuario por día requerido")
    @Min(value = 1, message = "El máximo de sesiones por usuario por día debe ser mayor a 0")
    private Integer maxSessionsPerUserPerDay;


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

    @NotNull(message = "La configuración del juego es requerida")
    @Valid
    private GameConfigDTO gameConfig;
}