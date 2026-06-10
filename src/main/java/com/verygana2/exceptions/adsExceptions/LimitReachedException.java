package com.verygana2.exceptions.adsExceptions;

public class LimitReachedException extends RuntimeException {

    public LimitReachedException(String message) {
        super(message);
    }
}
