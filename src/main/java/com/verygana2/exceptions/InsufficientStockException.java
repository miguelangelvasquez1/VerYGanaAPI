package com.verygana2.exceptions;

public class InsufficientStockException extends RuntimeException{

    public InsufficientStockException(String productName){
        super("Insufficient stock for product: " + productName );
    }
    
}
