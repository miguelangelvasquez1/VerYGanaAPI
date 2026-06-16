package com.verygana2.dtos.survey;

import java.time.ZonedDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
public class StartSurveyResponse {
    private Long sessionId;
    private ZonedDateTime expiresAt;
    private SurveySessionDTO survey;
}
