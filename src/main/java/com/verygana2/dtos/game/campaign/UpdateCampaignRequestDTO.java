package com.verygana2.dtos.game.campaign;

import java.math.BigDecimal;
import java.util.List;

import com.verygana2.models.enums.TargetGender;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateCampaignRequestDTO {
    
    // Sistema de monedas y recompensas
    @DecimalMin(value = "0.01", message = "Valor mínimo por moneda: $0.01")
    @DecimalMax(value = "100.00", message = "Valor máximo por moneda: $100.00")
    private BigDecimal coinValue;

    @Min(value = 0, message = "Las monedas de completación no pueden ser negativas")
    private Integer completionCoins;

    @Min(value = 1, message = "El presupuesto en monedas debe ser mayor a 0")
    private Integer budgetCoins;

    @Min(value = 1, message = "El máximo de monedas por sesión debe ser mayor a 0")
    private Integer maxCoinsPerSession;

    @Min(value = 1, message = "El máximo de sesiones por usuario por día debe ser mayor a 0")
    private Integer maxSessionsPerUserPerDay;

    private String targetUrl;
    private List<Long> categoryIds;
    private TargetAudienceDTO targetAudience;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public class TargetAudienceDTO {

        private int minAge;
        private int maxAge;
        private TargetGender gender;
        private List<String> municipalityCodes;
    }
}
