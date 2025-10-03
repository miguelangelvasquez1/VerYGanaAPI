package com.VerYGana.exceptions.adsExceptions;

import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.http.HttpStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class AdNotFoundException extends RuntimeException {
    
    public AdNotFoundException(String message) {
        super(message);
    }
    
    public AdNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
    
    public AdNotFoundException(Long adId) {
        super("Anuncio no encontrado con ID: " + adId);
    }
}