package com.verygana2.exceptions.payoutExceptions;

public class PayoutMethodRequiredException extends RuntimeException {
    public PayoutMethodRequiredException(String message) {
        super(message);
    }
}
