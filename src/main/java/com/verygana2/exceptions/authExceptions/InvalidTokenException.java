package com.verygana2.exceptions.authExceptions;

import org.springframework.security.oauth2.jwt.JwtException;

public class InvalidTokenException extends RuntimeException {
    
    public InvalidTokenException(String message) {
        super(message);
    }

    public InvalidTokenException(String message, JwtException cause) {
        super(message + ": " + cause.getMessage());
    }
}
