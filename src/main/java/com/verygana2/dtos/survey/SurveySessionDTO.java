package com.verygana2.dtos.survey;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
public class SurveySessionDTO {
    private Long id;
    private String title;
    private String description;
    private Long rewardAmountPerQuestionCents;
    private List<QuestionResponse> questions;
}
