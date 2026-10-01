package com.verygana2.dtos.branding;

import java.time.ZonedDateTime;
import java.util.List;

import com.verygana2.models.enums.TargetGender;
import com.verygana2.utils.validators.MinTargetAge;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class UpdateBrandingRequestConfigDTO {

    // Segmentación de audiencia
    private List<Long> categoryIds;
    private List<String> municipalityCodes;

    @MinTargetAge(message = "Minimum age must be at least {minAge}")
    @Max(value = 100)
    private Integer minAge;

    @MinTargetAge
    @Max(value = 100, message = "Maximum age must not exceed 100")
    private Integer maxAge;

    private TargetGender targetGender;

    @Min(value = 1, message = "Max sessions per user per day must be at least 1")
    private Integer maxSessionsPerUserPerDay;

    private ZonedDateTime startDate;
}
