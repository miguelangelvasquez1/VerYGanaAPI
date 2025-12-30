package com.verygana2.exceptions;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.hibernate.exception.JDBCConnectionException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MultipartException;

import com.verygana2.exceptions.adsExceptions.AdNotFoundException;
import com.verygana2.exceptions.adsExceptions.DuplicateLikeException;
import com.verygana2.exceptions.adsExceptions.InsufficientBudgetException;
import com.verygana2.exceptions.adsExceptions.InvalidAdStateException;
import com.verygana2.exceptions.authExceptions.InvalidTokenException;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.ValidationException;
import lombok.extern.slf4j.Slf4j;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    
    //Predetermined exception handler for Exception
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGlobalException(Exception ex) {
        // ex.printStackTrace();
        return buildError(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage());
    }

    //*For error 403 with no scope */
    @ExceptionHandler(AuthorizationDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAuthorizationDenied(AuthorizationDeniedException ex) {
        return buildError(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    //*For refresh token invalid */
    @ExceptionHandler(InvalidTokenException.class)
    public ResponseEntity<ErrorResponse> handleInvalidTokenException(InvalidTokenException ex) {
        return buildError(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    //* For jdbc connection failure */
    @ExceptionHandler(JDBCConnectionException.class)
    public ResponseEntity<ErrorResponse> handleJDBCConnectionException(JDBCConnectionException ex) {
        return buildError(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    //*For invalid credentials */
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentialsException(BadCredentialsException ex) {
        return buildError(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    /* For ad not found 404 */
    @ExceptionHandler(AdNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleAdNotFoundException(AdNotFoundException ex) {
        log.info("AdNotFoundException: {}", ex.getMessage());
        return buildError(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    /* For storage exception */
    @ExceptionHandler(StorageException.class)
    public ResponseEntity<ErrorResponse> handleStorageException(StorageException ex) {
        return buildError(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    /* For duplicate like 409 */
    @ExceptionHandler(DuplicateLikeException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateLikeException(DuplicateLikeException ex) {
        log.error("DuplicateLikeException: {}", ex.getMessage());
        return buildError(HttpStatus.CONFLICT, ex.getMessage());
    }

    /* For insufficient budget 400 */
    @ExceptionHandler(InsufficientBudgetException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientBudgetException(InsufficientBudgetException ex) {
        log.error("InsufficientBudgetException: {}", ex.getMessage());
        return buildError(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    /* For invalid ad state 400 */
    @ExceptionHandler(InvalidAdStateException.class)
    public ResponseEntity<ErrorResponse> handleInvalidAdStateException(InvalidAdStateException ex) {
        log.error("InvalidAdStateException: {}", ex.getMessage());
        return buildError(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    /* For user not found 500 */
    @ExceptionHandler(UsernameNotFoundException.class)
    public ResponseEntity<String> handleUsernameNotFoundException(UsernameNotFoundException ex) {
        return new ResponseEntity<>(ex.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /* For email already exists 500 */
    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<String> emailAlreadyExistsException(EmailAlreadyExistsException ex) {
        return new ResponseEntity<>(ex.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /* For phone number already exists 409 */
    @ExceptionHandler(PhoneNumberAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handlePhoneExists(PhoneNumberAlreadyExistsException ex) {
        return buildError(HttpStatus.CONFLICT, ex.getMessage());
    }

    /* For ValidationExceptions 500 */
    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(ValidationException ex) {
        return buildError(HttpStatus.CONFLICT, ex.getMessage());
    }

    /* For multipart exception 500 */
    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ErrorResponse> handleMultipartException(MultipartException ex) {
        return buildError(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    //DataIntegrityViolation

    // Jakarta Validation
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolationException(ConstraintViolationException ex) {
        // Extraer todos los mensajes de validación
        String messages = ex.getConstraintViolations()
                .stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining("; "));

        return buildError(HttpStatus.BAD_REQUEST, messages);
    }

    // @Valid for DTOs
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errors.put(error.getField(), error.getDefaultMessage());
        }

        ErrorResponse errorResponse = ErrorResponse.builder()
            .timestamp(Instant.now())
            .status(HttpStatus.BAD_REQUEST.value())
            .error("Validation Error")
            .message("Validation failed for one or more fields")
            .details(errors)
            .build();

        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    /* For build error method */
    private ResponseEntity<ErrorResponse> buildError(HttpStatus status, String message) { //Add request to include path
        ErrorResponse error = ErrorResponse.builder()
            .timestamp(Instant.now())
            .status(status.value())
            .error(status.getReasonPhrase())
            .message(message)
            .build();
        return new ResponseEntity<>(error, status);
    }
}
