package com.verygana2.dtos.game.campaign;

import java.util.List;

import com.verygana2.models.enums.TargetGender;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateCampaignRequestDTO {

    @Min(value = 1, message = "El máximo de sesiones por usuario por día debe ser mayor a 0")
    private Integer maxSessionsPerUserPerDay;

    private List<Long> categoryIds;

    // Sin @Valid, las restricciones de la clase interna no se evalúan.
    @Valid
    private TargetAudienceDTO targetAudience;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public class TargetAudienceDTO {

        @Min(value = 18, message = "La edad mínima debe ser al menos 18")
        @Max(value = 100, message = "La edad mínima no puede superar 100")
        private Integer minAge;

        @Min(value = 18, message = "La edad máxima debe ser al menos 18")
        @Max(value = 100, message = "La edad máxima no puede superar 100")
        private Integer maxAge;

        private TargetGender gender;
        private List<String> municipalityCodes;
    }
}
