package com.verygana2.exceptions.surveys;

public class SurveyNotFoundException extends RuntimeException {
    public SurveyNotFoundException(Long surveyId) {
        super("Survey with ID " + surveyId + " not found.");
    }
}
