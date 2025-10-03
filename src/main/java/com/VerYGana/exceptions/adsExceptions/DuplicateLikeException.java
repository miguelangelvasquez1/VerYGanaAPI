package com.VerYGana.exceptions.adsExceptions;

import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.http.HttpStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class DuplicateLikeException extends RuntimeException {
    
    public DuplicateLikeException(String message) {
        super(message);
    }
    
    public DuplicateLikeException(Long userId, Long adId) {
        super(String.format("El usuario %d ya dio like al anuncio %d", userId, adId));
    }
}