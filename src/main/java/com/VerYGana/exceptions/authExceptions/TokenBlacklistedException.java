package com.VerYGana.exceptions.authExceptions;

public class TokenBlacklistedException extends RuntimeException {
    
    public TokenBlacklistedException(String message) {
        super(message);
    }   
}
