package com.VerYGana.exceptions;

public class InsufficientFunds extends RuntimeException {
    public InsufficientFunds(){
        super("Insufficient funds");
    }
}
