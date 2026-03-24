package com.verygana2.dtos.survey;

import java.time.LocalDateTime;
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
 
    private Integer maxResponses;
 
    private LocalDateTime startsAt;
    private LocalDateTime endsAt;
 
    // Targeting
    @NotNull @Size(min = 1)
    private List<Long> categoryIds;
 
    private List<String> municipalityCodes;
 
    @Min(13) private Integer minAge;
    @Max(100) private Integer maxAge;
 
    private TargetGender targetGender;
 
    @Valid @NotEmpty
    private List<CreateQuestionRequest> questions;
}