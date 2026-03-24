package com.verygana2.dtos.survey;

import java.util.List;

import com.verygana2.models.surveys.SurveyQuestion;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor 
@AllArgsConstructor 
@Builder
@Data
public class CreateQuestionRequest {
 
        @NotBlank @Size(max = 500)
        private String text;
 
        @NotNull
        private SurveyQuestion.QuestionType type;
 
        @Builder.Default
        private Boolean required = true;
 
        private List<String> options; // only for SINGLE_CHOICE / MULTIPLE_CHOICE
    }