package com.verygana2.exceptions;

public class InvalidRequestException extends RuntimeException{
    
    public InvalidRequestException(String message){
        super(message);
    }
}
