package com.verygana2.dtos.survey;

import java.util.List;

import com.verygana2.models.enums.TargetGender;
import com.verygana2.utils.validators.MinTargetAge;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
public class UpdateSurveyRequest {

    @Size(max = 200)
    private String title;

    @Size(max = 1000)
    private String description;

    private List<Long> categoryIds;

    private List<String> municipalityCodes;

    @MinTargetAge
    private Integer minAge;

    @Max(100)
    private Integer maxAge;

    private TargetGender targetGender;
}
