package com.verygana2.exceptions.surveys;

public class SurveyAlreadyCompletedException extends RuntimeException {
    public SurveyAlreadyCompletedException(String message) {
        super(message);
    }

    public SurveyAlreadyCompletedException() {
        super("User already completed this survey");
    }
}
