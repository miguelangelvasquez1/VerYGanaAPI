package com.verygana2.exceptions.adsExceptions;

import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.http.HttpStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InsufficientBudgetException extends RuntimeException {
    
    public InsufficientBudgetException(String message) {
        super(message);
    }
    
    public InsufficientBudgetException(Long adId) {
        super("El anuncio " + adId + " no tiene presupuesto suficiente");
    }
}