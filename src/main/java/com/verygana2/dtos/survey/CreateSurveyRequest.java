package com.verygana2.dtos.survey;

import java.util.List;

import com.verygana2.models.enums.TargetGender;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor 
@AllArgsConstructor 
@Builder
@Data
public class CreateSurveyRequest { 
    
    @NotBlank
    @Size(max = 200)
    private String title;

    @Size(max = 1000)
    private String description;

    @NotNull @Min(1)
    private Integer maxResponses;

    /** Reward paid to each respondent per question answered. Must be >= system minimum. */
    @NotNull @Min(1)
    private Long pricePerQuestionCents;

    // Targeting
    @NotNull @Size(min = 1)
    private List<Long> categoryIds;
 
    private List<String> municipalityCodes;
 
    @Min(13) private Integer minAge;
    @Max(100) private Integer maxAge;
 
    private TargetGender targetGender;
 
    @Valid @NotEmpty @Size(max = 20)
    private List<CreateQuestionRequest> questions;
}