package com.verygana2.dtos.targeting;

import java.util.List;

import com.verygana2.models.enums.TargetGender;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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

    @Min(value = 13, message = "Minimum age must be at least 13")
    @Max(value = 100, message = "Minimum age cannot exceed 100")
    private Integer minAge;

    @Min(value = 13, message = "Minimum age must be at least 13")
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
