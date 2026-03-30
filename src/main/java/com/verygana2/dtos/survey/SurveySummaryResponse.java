package com.verygana2.dtos.survey;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.verygana2.models.surveys.Survey;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor 
@AllArgsConstructor 
@Builder
@Data
public class SurveySummaryResponse {

    private Long id;
    private String title;
    private String description;
    private BigDecimal rewardAmount;
    private Survey.SurveyStatus status;
    private boolean alreadyCompleted;
    private Long totalResponses;
    private LocalDateTime createdAt;
}