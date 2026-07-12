package com.verygana2.dtos.survey;

import java.time.LocalDateTime;
import java.util.List;

import com.verygana2.models.surveys.Survey;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
public class SurveyDetailDTO {
    private Long id;
    private String title;
    private String description;
    private Long totalRewardKeys;
    private Integer maxResponses;
    private Integer responseCount;
    private Survey.SurveyStatus status;
    private LocalDateTime startsAt;
    private LocalDateTime endsAt;
    private List<String> categoryNames;
    private int totalQuestions;
    private String companyName;
}
