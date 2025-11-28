package com.verygana2.exceptions;

public class ProductNotAvailableException extends RuntimeException{
    
    public ProductNotAvailableException(String productName){
        super(productName + " is not active in this moment");
    }
}
