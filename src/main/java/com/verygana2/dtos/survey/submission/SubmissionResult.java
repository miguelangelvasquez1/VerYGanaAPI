package com.verygana2.dtos.survey.submission;

import com.verygana2.models.surveys.SurveyResponse.ResponseStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor 
@AllArgsConstructor 
@Builder
@Data
public class SubmissionResult {
        private Long responseId;
        private ResponseStatus status;
        private RewardInfo reward;
        private String message;
    }