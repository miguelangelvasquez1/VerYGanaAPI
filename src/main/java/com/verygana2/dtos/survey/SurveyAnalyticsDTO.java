package com.verygana2.dtos.survey;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor 
@AllArgsConstructor 
@Builder
@Data
public class SurveyAnalyticsDTO {
    
    private Long   surveyId;
    private String surveyTitle;
 
    private int    totalResponses;
    private int    completedResponses;
 
    /** completedResponses / totalResponses × 100, rounded to 1 decimal */
    private double completionRate;
 
    /** Average minutes from startedAt to completedAt. Null if no completions. */
    private Double averageCompletionMinutes;
 
    private List<QuestionStatDTO> questionStats;

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class QuestionStatDTO {
        private Long   questionId;
        private String questionText;
        private String questionType;   // TEXT | RATING | SINGLE_CHOICE | MULTIPLE_CHOICE | YES_NO
        private int    totalAnswers;
 
        /** Filled for SINGLE_CHOICE, MULTIPLE_CHOICE, YES_NO */
        private List<OptionStatDTO> optionStats;
 
        /** Filled for RATING. Null for other types. */
        private Double averageRating;
 
        /** Filled for TEXT. Up to 50 sample answers. Empty for other types. */
        private List<String> textAnswers;
    }
 
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class OptionStatDTO {
        private String optionText;
        private int    count;
 
        /** count / totalAnswers × 100 */
        private double percentage;
    }
}
