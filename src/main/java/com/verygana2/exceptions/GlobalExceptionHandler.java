package com.verygana2.exceptions;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import jakarta.persistence.EntityNotFoundException;
import org.hibernate.ObjectNotFoundException;
import org.hibernate.exception.JDBCConnectionException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;

import com.verygana2.exceptions.adsExceptions.AdNotFoundException;
import com.verygana2.exceptions.adsExceptions.DuplicateLikeException;
import com.verygana2.exceptions.adsExceptions.InsufficientBudgetException;
import com.verygana2.exceptions.adsExceptions.InvalidAdStateException;
import com.verygana2.exceptions.adsExceptions.LimitReachedException;
import com.verygana2.exceptions.authExceptions.InvalidTokenException;
import com.verygana2.exceptions.payoutExceptions.InvalidPayoutMethodStateException;
import com.verygana2.exceptions.payoutExceptions.OtpVerificationException;
import com.verygana2.exceptions.payoutExceptions.PayoutMethodNotFoundException;
import com.verygana2.exceptions.rafflesExceptions.ClaimPrizeException;
import com.verygana2.exceptions.rafflesExceptions.InvalidOperationException;
import com.verygana2.exceptions.surveys.SurveyAlreadyCompletedException;
import com.verygana2.exceptions.surveys.SurveyNotActiveException;
import com.verygana2.exceptions.surveys.SurveyNotFoundException;
import com.verygana2.services.plans.PlanFeatureGuard.PlanCapabilityException;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.ValidationException;
import lombok.extern.slf4j.Slf4j;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // ==================== EXCEPCIONES GENÉRICAS ====================

    @ExceptionHandler(AsyncRequestTimeoutException.class)
    public ResponseEntity<ErrorResponse> handleAsyncRequestTimeout(AsyncRequestTimeoutException ex, WebRequest request) {
        log.debug("SSE connection timed out for {}", request.getDescription(false));
        return buildError(HttpStatus.SERVICE_UNAVAILABLE, "SSE connection timed out", request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGlobalException(Exception ex, WebRequest request) {
        log.error("Unexpected error: ", ex);
        return buildError(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error", request);
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException ex, WebRequest request) {
        log.warn("Business rule violation: {}", ex.getMessage());
        return buildError(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage(), request);
    }

    // ==================== AUTENTICACIÓN Y AUTORIZACIÓN ====================

    @ExceptionHandler(AuthorizationDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAuthorizationDenied(
            AuthorizationDeniedException ex, WebRequest request) {
        log.warn("Authorization denied: {}", ex.getMessage());
        return buildError(HttpStatus.FORBIDDEN, "Access denied", request);
    }

    @ExceptionHandler(InvalidTokenException.class)
    public ResponseEntity<ErrorResponse> handleInvalidTokenException(
            InvalidTokenException ex, WebRequest request) {
        log.warn("Invalid token: {}", ex.getMessage());
        return buildError(HttpStatus.UNAUTHORIZED, ex.getMessage(), request);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentialsException(
            BadCredentialsException ex, WebRequest request) {
        log.warn("Bad credentials: {}", ex.getMessage());
        return buildError(HttpStatus.UNAUTHORIZED, "Invalid credentials", request);
    }

    // ==================== RECURSOS NO ENCONTRADOS (404) ====================

    @ExceptionHandler(AdNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleAdNotFoundException(
            AdNotFoundException ex, WebRequest request) {
        log.info("Ad not found: {}", ex.getMessage());
        return buildError(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler(UsernameNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleUsernameNotFoundException(
            UsernameNotFoundException ex, WebRequest request) {
        log.warn("User not found: {}", ex.getMessage());
        return buildError(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler(ObjectNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleObjectNotFoundException(ObjectNotFoundException ex, WebRequest request) {
        log.warn("Object not found : {}", ex.getMessage());
        return buildError(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }


    @ExceptionHandler(FeatureDisabledException.class)
    public ResponseEntity<ErrorResponse> handleFeatureDisabledException(FeatureDisabledException ex, WebRequest request) {
        log.warn("Feature disabled: {}", ex.getMessage());
        return buildError(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage(), request); //503
    }

    // ==================== CONFLICTOS (409) ====================

    @ExceptionHandler(DuplicateLikeException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateLikeException(
            DuplicateLikeException ex, WebRequest request) {
        log.warn("Duplicate like: {}", ex.getMessage());
        return buildError(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    @ExceptionHandler(LimitReachedException.class)
    public ResponseEntity<ErrorResponse> handleLimitReachedException(
            LimitReachedException ex, WebRequest request) {
        log.warn("Limit reached: {}", ex.getMessage());
        return buildError(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage(), request);
    }

    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleEmailAlreadyExistsException(
            EmailAlreadyExistsException ex, WebRequest request) {
        log.warn("Email already exists: {}", ex.getMessage());
        return buildError(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    @ExceptionHandler(PhoneNumberAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handlePhoneNumberAlreadyExistsException(
            PhoneNumberAlreadyExistsException ex, WebRequest request) {
        log.warn("Phone number already exists: {}", ex.getMessage());
        return buildError(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    // ==================== ERRORES DE VALIDACIÓN (400) ====================

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(IllegalArgumentException ex, WebRequest request) {
        log.warn("Illegal argument: {}", ex.getMessage());
        return buildError(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    @ExceptionHandler(ClaimPrizeException.class)
    public ResponseEntity<ErrorResponse> handleClaimPrizeException(ClaimPrizeException ex, WebRequest request) {
        log.warn("Claim prize error: {}", ex.getMessage());
        return buildError(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage(), request);
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(ValidationException ex, WebRequest request) {
        log.warn("Validation error: {}", ex.getMessage());
        return buildError(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    @ExceptionHandler(InsufficientBudgetException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientBudgetException(
            InsufficientBudgetException ex, WebRequest request) {
        log.warn("Insufficient budget: {}", ex.getMessage());
        return buildError(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    @ExceptionHandler(InvalidAdStateException.class)
    public ResponseEntity<ErrorResponse> handleInvalidAdStateException(
            InvalidAdStateException ex, WebRequest request) {
        log.warn("Invalid ad state: {}", ex.getMessage());
        return buildError(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    @ExceptionHandler(FavoriteProductException.class)
    public ResponseEntity<ErrorResponse> handleFavoriteProductException(
            FavoriteProductException ex, WebRequest request) {
        log.warn("Favorite product error: {}", ex.getMessage());
        return buildError(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    @ExceptionHandler(PlanCapabilityException.class)
    public ResponseEntity<ErrorResponse> handlePlanCapabilityException(
            PlanCapabilityException ex, WebRequest request) {
        log.warn("Plan capability error: {}", ex.getMessage());
        return buildError(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    @ExceptionHandler(InsufficientStockException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientStockException(
            InsufficientStockException ex, WebRequest request) {
        log.warn("Insufficient stock: {}", ex.getMessage());
        return buildError(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    @ExceptionHandler(StorageException.class)
    public ResponseEntity<ErrorResponse> handleStorageException(
            StorageException ex, WebRequest request) {
        log.warn("Storage error: {}", ex.getMessage());
        return buildError(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    @ExceptionHandler(SurveyNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleSurveyNotFoundException(
            SurveyNotFoundException ex, WebRequest request) {
        log.warn("Survey not found: {}", ex.getMessage());
        return buildError(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler(SurveyNotActiveException.class)
    public ResponseEntity<ErrorResponse> handleSurveyNotActiveException(
            SurveyNotActiveException ex, WebRequest request) {
        log.warn("Survey not active: {}", ex.getMessage());
        return buildError(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    @ExceptionHandler(SurveyAlreadyCompletedException.class)
    public ResponseEntity<ErrorResponse> handleSurveyAlreadyCompletedException(
            SurveyAlreadyCompletedException ex, WebRequest request) {
        log.warn("Survey already completed: {}", ex.getMessage());
        return buildError(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    @ExceptionHandler(JDBCConnectionException.class)
    public ResponseEntity<ErrorResponse> handleJDBCConnectionException(
            JDBCConnectionException ex, WebRequest request) {
        log.error("JDBC connection error: {}", ex.getMessage());
        return buildError(HttpStatus.SERVICE_UNAVAILABLE, "Database connection error", request);
    }

    @ExceptionHandler(InvalidRequestException.class)
    public ResponseEntity<ErrorResponse> handleInvalidRequestException(
            InvalidRequestException ex, WebRequest request) {
        log.warn("Invalid request error: {}", ex.getMessage());
        return buildError(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    @ExceptionHandler(InvalidOperationException.class)
    public ResponseEntity<ErrorResponse> handleInvalidOperationException(
            InvalidOperationException ex, WebRequest request) {
        log.warn("Invalid operation error: {}", ex.getMessage());
        return buildError(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    @ExceptionHandler(InvalidContentException.class)
    public ResponseEntity<ErrorResponse> handleInvalidContentException(
            InvalidContentException ex, WebRequest request) {
        log.warn("Invalid content error: {}", ex.getMessage());
        return buildError(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    @ExceptionHandler(GameRewardException.class)
    public ResponseEntity<ErrorResponse> handleGameRewardException(
            GameRewardException ex, WebRequest request) {
        log.warn("Game reward error: {}", ex.getMessage());
        return buildError(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    // ==================== PAYOUT METHODS ====================

    @ExceptionHandler(PayoutMethodNotFoundException.class)
    public ResponseEntity<ErrorResponse> handlePayoutMethodNotFoundException(
            PayoutMethodNotFoundException ex, WebRequest request) {
        log.warn("Payout method not found: {}", ex.getMessage());
        return buildError(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler(OtpVerificationException.class)
    public ResponseEntity<ErrorResponse> handleOtpVerificationException(
            OtpVerificationException ex, WebRequest request) {
        log.warn("OTP verification failed: {}", ex.getMessage());
        return buildError(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    @ExceptionHandler(InvalidPayoutMethodStateException.class)
    public ResponseEntity<ErrorResponse> handleInvalidPayoutMethodStateException(
            InvalidPayoutMethodStateException ex, WebRequest request) {
        log.warn("Invalid payout method state: {}", ex.getMessage());
        return buildError(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingServletRequestParameterException(
        MissingServletRequestParameterException ex, WebRequest request) {
        log.warn("Missing servlet request parameter error: {}", ex.getMessage());
        return buildError(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    // ==================== VALIDACIONES JAKARTA ====================

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolationException(
            ConstraintViolationException ex, WebRequest request) {

        String messages = ex.getConstraintViolations()
                .stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining("; "));

        log.warn("Constraint violation: {}", messages);
        return buildError(HttpStatus.BAD_REQUEST, messages, request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValidException(
            MethodArgumentNotValidException ex, WebRequest request) {

        Map<String, String> errors = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errors.put(error.getField(), error.getDefaultMessage());
        }

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Validation Error")
                .message("Validation failed for one or more fields")
                .path(getPath(request))
                .details(errors)
                .build();

        log.warn("Validation failed: {}", errors);
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(TransactionSystemException.class)
    public ResponseEntity<ErrorResponse> handleTransactionSystemException(
            TransactionSystemException ex, WebRequest request) {

        Throwable cause = ex.getRootCause();

        if (cause instanceof jakarta.validation.ConstraintViolationException violationEx) {
            String message = violationEx.getConstraintViolations()
                    .stream()
                    .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                    .findFirst()
                    .orElse("Invalid data");

            log.warn("Transaction validation error: {}", message);
            return buildError(HttpStatus.BAD_REQUEST, message, request);
        }

        log.error("Transaction system error: ", ex);
        return buildError(HttpStatus.BAD_REQUEST, "Transaction processing failed", request);
    }

    // ── 404 ───────────────────────────────────────────────────────────────────
    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleEntityNotFound(EntityNotFoundException ex, WebRequest request) {
        log.warn("Entity not found: {}", ex.getMessage());
        return buildError(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    // ── 401 ───────────────────────────────────────────────────────────────────
    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorized(UnauthorizedException ex, WebRequest request) {
        log.warn("Unauthorized: {}", ex.getMessage());
        return buildError(HttpStatus.UNAUTHORIZED, ex.getMessage(), request);
    }

    // ==================== MÉTODOS AUXILIARES ====================

    private ResponseEntity<ErrorResponse> buildError(
            HttpStatus status, String message, WebRequest request) {

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(status.value())
                .error(status.getReasonPhrase())
                .message(message)
                .path(getPath(request))
                .build();

        return new ResponseEntity<>(error, status);
    }

    private String getPath(WebRequest request) {
        return request.getDescription(false).replace("uri=", "");
    }
}