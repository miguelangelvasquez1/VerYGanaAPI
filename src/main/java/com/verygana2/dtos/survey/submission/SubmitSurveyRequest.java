package com.verygana2.dtos.survey.submission;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor 
@AllArgsConstructor 
@Builder
@Data
public class SubmitSurveyRequest {
 
    @NotNull
    private Long surveyId;

    @Valid @NotEmpty
        private List<AnswerRequest> answers;
    }