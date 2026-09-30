package com.verygana2.dtos.targeting;

import java.util.List;

import com.verygana2.models.enums.TargetGender;
import com.verygana2.utils.validators.MinTargetAge;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OptionalTargetAudienceDTO {

    /** null o vacío = disponible en todas las localidades */
    private List<String> municipalityCodes;

    @MinTargetAge(message = "Minimum age must be at least {minAge}")
    @Max(value = 100, message = "Minimum age cannot exceed 100")
    private Integer minAge;

    @MinTargetAge(message = "Maximum age must be at least {minAge}")
    @Max(value = 100, message = "Maximum age cannot exceed 100")
    private Integer maxAge;

    /** null o ALL = sin restricción de género */
    private TargetGender targetGender;

    @AssertTrue(message = "Maximum age must be greater than or equal to minimum age")
    public boolean isValidAgeRange() {
        if (minAge == null || maxAge == null) {
            return true;
        }
        return maxAge >= minAge;
    }
}
