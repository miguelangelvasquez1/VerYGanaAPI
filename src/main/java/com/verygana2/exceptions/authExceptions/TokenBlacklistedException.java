package com.verygana2.exceptions.authExceptions;

public class TokenBlacklistedException extends RuntimeException {
    
    public TokenBlacklistedException(String message) {
        super(message);
    }   
}
