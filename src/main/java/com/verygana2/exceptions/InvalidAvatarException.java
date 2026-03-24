package com.verygana2.exceptions;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidAvatarException extends RuntimeException {
    public InvalidAvatarException(String message) {
        super(message);
    }
}
