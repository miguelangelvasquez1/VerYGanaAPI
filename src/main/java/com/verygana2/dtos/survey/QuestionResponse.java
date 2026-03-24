package com.verygana2.dtos.survey;

import java.util.List;

import com.verygana2.models.surveys.SurveyQuestion;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor 
@AllArgsConstructor 
@Builder
@Data
public class QuestionResponse {

    private Long id;
    private String text;
    private SurveyQuestion.QuestionType type;
    private Boolean required;
    private Integer orderIndex;
    private List<OptionResponse> options;
}