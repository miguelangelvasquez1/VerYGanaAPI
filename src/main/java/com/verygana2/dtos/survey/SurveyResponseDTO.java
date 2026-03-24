package com.verygana2.dtos.survey;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.verygana2.models.enums.TargetGender;
import com.verygana2.models.surveys.Survey;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor 
@AllArgsConstructor 
@Builder
@Data
public class SurveyResponseDTO {
    private Long id;
    private String title;
    private String description;
    private BigDecimal rewardAmount;
    private Integer responseCount;
    private Integer maxResponses;
    private Survey.SurveyStatus status;
    private LocalDateTime startsAt;
    private LocalDateTime endsAt;
    private LocalDateTime createdAt;
 
    // Targeting info
    private List<String> categoryNames;
    private List<String> municipalityNames;
    private Integer minAge;
    private Integer maxAge;
    private TargetGender targetGender;
 
    private List<QuestionResponse> questions;
}