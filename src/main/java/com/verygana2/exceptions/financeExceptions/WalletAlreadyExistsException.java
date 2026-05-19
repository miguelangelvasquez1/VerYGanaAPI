package com.verygana2.exceptions.financeExceptions;

public class WalletAlreadyExistsException extends RuntimeException{
    
    public WalletAlreadyExistsException(String message) {
        super(message);
    }
}
