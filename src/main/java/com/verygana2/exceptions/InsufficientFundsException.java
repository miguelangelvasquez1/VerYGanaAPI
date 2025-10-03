package com.verygana2.exceptions;

public class InsufficientFundsException extends RuntimeException {

    public InsufficientFundsException(){
        super("Insufficient Funds");
    }

    public InsufficientFundsException(String description){
        super("Insufficient Funds" + description);
    }
}
