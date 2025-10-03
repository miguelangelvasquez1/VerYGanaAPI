package com.verygana2.exceptions;

public class InvalidAmountException extends RuntimeException{
    public InvalidAmountException (String reason){
        super("Invalid amount, " + reason);
    }
}
