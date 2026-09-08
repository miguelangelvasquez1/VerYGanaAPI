package com.verygana2.exceptions;

public class InvalidAmountException extends IllegalArgumentException{
    public InvalidAmountException (String reason){
        super("Invalid amount, " + reason);
    }
}
