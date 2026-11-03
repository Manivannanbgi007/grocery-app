package com.grocery.store.exception;

import com.grocery.store.dto.common.ApiResponse;
import com.grocery.store.dto.common.ErrorDetail;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

/**
 * Turns every exception into the standard {@link ApiResponse} error body,
 * so clients always receive the same JSON shape.
 *
 * Status codes used across all services:
 *   400 invalid request or broken business rule
 *   401 not logged in / wrong credentials
 *   403 logged in but not allowed
 *   404 record or endpoint not found
 *   405 HTTP method not supported
 *   409 duplicate record or concurrent update
 *   500 unexpected error (details only in the log)
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ----- our own exceptions -----

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage(), null, request);
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ApiResponse<Void>> handleDuplicate(DuplicateResourceException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.CONFLICT, ex.getMessage(), null, request);
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessRule(BusinessException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage(), null, request);
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnauthorized(UnauthorizedException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.UNAUTHORIZED, ex.getMessage(), null, request);
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ApiResponse<Void>> handleForbidden(ForbiddenException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.FORBIDDEN, ex.getMessage(), null, request);
    }

    // ----- validation errors -----

    /** @Valid on a @RequestBody failed: one ErrorDetail per invalid field. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidBody(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<ErrorDetail> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> new ErrorDetail(fieldError.getField(), fieldError.getDefaultMessage()))
                .toList();
        return buildResponse(HttpStatus.BAD_REQUEST, "Validation failed", errors, request);
    }

    /** Constraint annotations on @RequestParam / @PathVariable failed. */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidParameters(HandlerMethodValidationException ex, HttpServletRequest request) {
        List<ErrorDetail> errors = ex.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> new ErrorDetail(result.getMethodParameter().getParameterName(), error.getDefaultMessage())))
                .toList();
        return buildResponse(HttpStatus.BAD_REQUEST, "Validation failed", errors, request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadableBody(HttpMessageNotReadableException ex, HttpServletRequest request) {
        log.debug("Unreadable request body: {}", ex.getMessage());
        return buildResponse(HttpStatus.BAD_REQUEST,
                "Request body is missing or invalid (check the JSON syntax and the allowed values)", null, request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        String message = "Invalid value '%s' for parameter '%s'".formatted(ex.getValue(), ex.getName());
        return buildResponse(HttpStatus.BAD_REQUEST, message, null, request);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParameter(MissingServletRequestParameterException ex, HttpServletRequest request) {
        String message = "Required parameter '%s' is missing".formatted(ex.getParameterName());
        return buildResponse(HttpStatus.BAD_REQUEST, message, null, request);
    }

    // ----- routing errors -----

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoEndpoint(NoResourceFoundException ex, HttpServletRequest request) {
        String message = "No endpoint %s %s".formatted(request.getMethod(), request.getRequestURI());
        return buildResponse(HttpStatus.NOT_FOUND, message, null, request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        String message = "HTTP method %s is not supported for this endpoint".formatted(ex.getMethod());
        return buildResponse(HttpStatus.METHOD_NOT_ALLOWED, message, null, request);
    }

    // ----- database conflicts -----

    /** Two requests updated the same row at the same time (@Version check failed). */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ApiResponse<Void>> handleConcurrentUpdate(ObjectOptimisticLockingFailureException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.CONFLICT,
                "This record was changed by someone else. Please reload it and try again.", null, request);
    }

    /** A database constraint was violated (for example a unique key hit by two parallel requests). */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrity(DataIntegrityViolationException ex, HttpServletRequest request) {
        log.warn("Data integrity violation on {}: {}", request.getRequestURI(), ex.getMostSpecificCause().getMessage());
        return buildResponse(HttpStatus.CONFLICT, "The request conflicts with existing data", null, request);
    }

    // ----- database busy -----

    /**
     * No database connection became free within 5 s (spring.datasource.hikari.connection-timeout): every connection is
     * in use. 503 tells the client to try again; the request did not change anything.
     */
    @ExceptionHandler(CannotCreateTransactionException.class)
    public ResponseEntity<ApiResponse<Void>> handleDatabaseBusy(CannotCreateTransactionException ex, HttpServletRequest request) {
        log.warn("No database connection for {} {}: {}", request.getMethod(), request.getRequestURI(),
                ex.getMostSpecificCause().getMessage());
        return buildResponse(HttpStatus.SERVICE_UNAVAILABLE, "The service is busy right now. Please try again in a moment.", null, request);
    }

    // ----- everything else -----

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unexpected error on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong. Please try again later.", null, request);
    }

    private ResponseEntity<ApiResponse<Void>> buildResponse(HttpStatus status, String message,
                                                            List<ErrorDetail> errors, HttpServletRequest request) {
        return ResponseEntity.status(status).body(ApiResponse.error(message, errors, request.getRequestURI()));
    }
}
