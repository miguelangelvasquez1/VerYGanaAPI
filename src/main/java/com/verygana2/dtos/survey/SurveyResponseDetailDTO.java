package com.verygana2.dtos.survey;

import java.time.ZonedDateTime;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
public class SurveyResponseDetailDTO {

    private Long   id;
    private String userHash;
    private String status;
    private ZonedDateTime completedAt;
    private List<AnswerDetailDTO> answers;

    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Data
    public static class AnswerDetailDTO {
        private Long   questionId;
        private String questionText;

        private String questionType;

        private String textAnswer;

        private String selectedOptionText;

        private List<String> selectedOptionTexts;
    }
}
