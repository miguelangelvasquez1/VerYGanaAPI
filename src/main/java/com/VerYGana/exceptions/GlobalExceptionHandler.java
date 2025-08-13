package com.VerYGana.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;

@ControllerAdvice
public class GlobalExceptionHandler {
    
    //Predetermined exception handler for IllegalArgumentException
    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleGlobalException(Exception ex, WebRequest request) {
        return new ResponseEntity<>("Ocurrió un error inesperado: " + ex.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(UsernameNotFoundException.class)
    public ResponseEntity<String> handleUsernameNotFoundException(UsernameNotFoundException ex) {
        return new ResponseEntity<>(ex.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<String> emailAlreadyExistsException(EmailAlreadyExistsException ex) {
        return new ResponseEntity<>(ex.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
    }

    // @ExceptionHandler(PhoneNumberAlreadyExistsException.class)
    // public ResponseEntity<ApiError> handlePhoneExists(PhoneNumberAlreadyExistsException ex, HttpServletRequest request) {
    //     return buildError(HttpStatus.CONFLICT, ex.getMessage(), request);
    // }

    // private ResponseEntity<ApiError> buildError(HttpStatus status, String message, HttpServletRequest request) {
    //     ApiError error = new ApiError(
    //         status.value(),
    //         status.getReasonPhrase(),
    //         message,
    //         Instant.now().toString(),
    //         request.getRequestURI()
    //     );
    //     return new ResponseEntity<>(error, status);
    // }

    //No defino el phoneexception pero igual sale
}
