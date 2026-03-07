package com.verygana2.dtos.game.campaign;

import java.util.List;

import com.verygana2.models.enums.TargetGender;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TargetAudienceDTO {
    
    @NotNull(message = "Minimum age is required")
    @Min(value = 13, message = "Minimum age must be at least 13")
    @Max(value = 100, message = "Minimum age cannot exceed 100")
    private Integer minAge;
    
    @NotNull(message = "Maximum age is required")
    @Min(value = 13, message = "Maximum age must be at least 13")
    @Max(value = 100, message = "Maximum age cannot exceed 100")
    private Integer maxAge;
    
    @NotNull(message = "Gender is required")
    private TargetGender gender;
    
    private List<String> municipalityCodes;
    
    // Custom validation
    @AssertTrue(message = "Maximum age must be greater than or equal to minimum age")
    public boolean isValidAgeRange() {
        if (minAge == null || maxAge == null) {
            return true; // Let @NotNull handle null validation
        }
        return maxAge >= minAge;
    }
}