package com.verygana2.dtos.game.campaign;

import java.util.List;

import com.verygana2.models.enums.TargetGender;
import com.verygana2.utils.validators.MinTargetAge;

import jakarta.validation.Valid;
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

    @Valid
    private TargetAudienceDTO targetAudience;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public class TargetAudienceDTO {

        @MinTargetAge(message = "Minimum age must be at least {minAge}")
        private Integer minAge;

        @MinTargetAge(message = "Maximum age must be at least {minAge}")
        private Integer maxAge;
        private TargetGender gender;
        private List<String> municipalityCodes;
    }
}
