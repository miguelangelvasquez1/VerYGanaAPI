package com.verygana2.dtos.survey.submission;

import com.verygana2.models.surveys.SurveySession.SessionStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
public class SubmissionResult {
    private Long sessionId;
    private SessionStatus status;
    private RewardInfo reward;
    private String message;
}
