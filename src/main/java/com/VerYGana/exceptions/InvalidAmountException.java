package com.VerYGana.exceptions;

public class InvalidAmountException extends RuntimeException{
    public InvalidAmountException (String reason){
        super("Invalid amount, " + reason);
    }
}
