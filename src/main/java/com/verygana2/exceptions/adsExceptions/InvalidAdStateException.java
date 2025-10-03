package com.verygana2.exceptions.adsExceptions;

import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.http.HttpStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidAdStateException extends RuntimeException {
    
    public InvalidAdStateException(String message) {
        super(message);
    }
    
    public InvalidAdStateException(String message, Throwable cause) {
        super(message, cause);
    }
}