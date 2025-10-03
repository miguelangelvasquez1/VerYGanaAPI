package com.verygana2.exceptions;

public class PhoneNumberAlreadyExistsException extends RuntimeException {
    public PhoneNumberAlreadyExistsException(String phoneNumber) {
        super("Phone number '" + phoneNumber + "' is already used.");
    }
    
}
