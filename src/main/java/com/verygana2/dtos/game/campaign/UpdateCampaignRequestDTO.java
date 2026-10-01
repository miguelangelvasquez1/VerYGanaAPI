package com.verygana2.dtos.game.campaign;

import java.util.List;

import com.verygana2.models.enums.TargetGender;
import com.verygana2.utils.validators.MinTargetAge;

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

        @MinTargetAge(message = "Minimum age must be at least {minAge}")
        @Max(value = 100, message = "Minimum age cannot exceed 100")
        private Integer minAge;

        @MinTargetAge(message = "Maximum age must be at least {minAge}")
        @Max(value = 100, message = "Maximum age cannot exceed 100")
        private Integer maxAge;

        private TargetGender gender;
        private List<String> municipalityCodes;
    }
}
