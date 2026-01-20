package com.verygana2.exceptions;

public class FavoriteProductExistsException extends RuntimeException{
    
    public FavoriteProductExistsException(String message){
        super(message);
    }
}
