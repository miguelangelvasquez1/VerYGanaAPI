package com.verygana2.exceptions.ProductStock;

public class DuplicateResourceException extends RuntimeException{
    
    public DuplicateResourceException(String message){
        super(message);
    }
}
