package com.verygana2.exceptions.rafflesExceptions;

public class LimitReachedException extends RuntimeException{
    
    public LimitReachedException(String message){
        super(message);
    }
}
