package com.verygana2.dtos.survey;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
public class AvailableSurveyDTO {
    private Long id;
    private String title;
    private String description;
    private Long totalRewardKeys;
    private int totalQuestions;
    private Integer maxResponses;
    private Integer responseCount;
    private LocalDateTime endsAt;
}
