package com.verygana2.exceptions.surveys;

public class SurveyNotActiveException extends RuntimeException {
    public SurveyNotActiveException(Long surveyId) {
        super("Survey with ID " + surveyId + " is not active.");
    }
    
}
