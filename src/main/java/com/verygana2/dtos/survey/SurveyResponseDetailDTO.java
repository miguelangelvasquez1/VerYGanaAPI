package com.verygana2.dtos.survey;

import java.time.LocalDateTime;
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
    private Long   userId;
    private String userName;
    private String status;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private List<AnswerDetailDTO> answers;

    @NoArgsConstructor 
    @AllArgsConstructor 
    @Builder
    @Data
    public static class AnswerDetailDTO {
        private Long   questionId;
        private String questionText;
 
        /** TEXT | RATING | SINGLE_CHOICE | MULTIPLE_CHOICE | YES_NO */
        private String questionType;
 
        /** Populated for TEXT and RATING questions */
        private String textAnswer;
 
        /** Populated for SINGLE_CHOICE and YES_NO */
        private String selectedOptionText;
 
        /** Populated for MULTIPLE_CHOICE */
        private List<String> selectedOptionTexts;
    }
}
