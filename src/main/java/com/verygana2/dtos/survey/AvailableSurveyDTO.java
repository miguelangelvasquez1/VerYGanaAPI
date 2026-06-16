package com.verygana2.dtos.survey;

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
    private Long rewardAmountPerQuestionCents;
    private int totalQuestions;
}
