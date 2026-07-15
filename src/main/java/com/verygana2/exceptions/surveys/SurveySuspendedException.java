package com.verygana2.exceptions.surveys;

public class SurveySuspendedException extends RuntimeException {
    public SurveySuspendedException(Long surveyId) {
        super("Survey with ID " + surveyId + " has been suspended by an administrator.");
    }
}
