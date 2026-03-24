package com.verygana2.dtos.survey.submission;

import java.util.List;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor 
@AllArgsConstructor 
@Builder
@Data
public class AnswerRequest {
 
        @NotNull
        private Long questionId;
 
        // For TEXT / RATING
        private String textAnswer;
 
        // For SINGLE_CHOICE / YES_NO
        private Long selectedOptionId;
 
        // For MULTIPLE_CHOICE
        private List<Long> selectedOptionIds;
    }