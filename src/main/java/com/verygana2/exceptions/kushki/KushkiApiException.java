package com.verygana2.exceptions.kushki;

public class KushkiApiException extends RuntimeException {

    public KushkiApiException(String message) {
        super(message);
    }

    public KushkiApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
