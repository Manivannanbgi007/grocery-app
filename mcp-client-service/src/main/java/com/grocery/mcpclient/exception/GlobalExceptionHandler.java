package com.grocery.mcpclient.exception;

import com.grocery.mcpclient.dto.common.ApiResponse;
import com.grocery.mcpclient.dto.common.ErrorDetail;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.NestedRuntimeException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

/**
 * Turns every exception into the standard {@link ApiResponse} error body,
 * so clients always receive the same JSON shape.
 *
 * Status codes:
 *   400 invalid message or conversation id; a recording that is empty, too large, unreadable or holds no question
 *   404 conversation not found (or it belongs to someone else) / unknown endpoint
 *   405 HTTP method not supported
 *   415 wrong content type (a recording must be multipart/form-data)
 *   500 unexpected error (details only in the log)
 *   503 the model, the tools or the speech server are not available, or the assistant is busy (with Retry-After), or
 *       the database cannot be reached
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** Seconds a client should wait before it sends the question again when the assistant is busy. */
    private static final String RETRY_AFTER_SECONDS = "10";

    // ----- our own exceptions -----

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage(), null, request);
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessRule(BusinessException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage(), null, request);
    }

    @ExceptionHandler(AssistantUnavailableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnavailable(AssistantUnavailableException ex, HttpServletRequest request) {
        log.warn("The assistant could not answer: {}", ex.getCause() == null ? ex.getMessage() : ex.getCause().toString());
        return buildResponse(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage(), null, request);
    }

    @ExceptionHandler(VoiceUnavailableException.class)
    public ResponseEntity<ApiResponse<Void>> handleVoiceUnavailable(VoiceUnavailableException ex, HttpServletRequest request) {
        log.warn("The speech server did not answer: {}", ex.getCause() == null ? ex.getMessage() : ex.getCause().toString());
        return buildResponse(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage(), null, request);
    }

    @ExceptionHandler(AssistantBusyException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusy(AssistantBusyException ex, HttpServletRequest request) {
        log.warn("All answer or voice slots are busy; the request was turned away");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER, RETRY_AFTER_SECONDS)
                .body(ApiResponse.error(ex.getMessage(), null, request.getRequestURI()));
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

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadableBody(HttpMessageNotReadableException ex, HttpServletRequest request) {
        log.debug("Unreadable request body: {}", ex.getMessage());
        return buildResponse(HttpStatus.BAD_REQUEST,
                "Request body is missing or invalid (check the JSON syntax; conversationId must be a UUID)", null, request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        String message = "Invalid value '%s' for parameter '%s'".formatted(ex.getValue(), ex.getName());
        return buildResponse(HttpStatus.BAD_REQUEST, message, null, request);
    }

    // ----- recordings (POST /transcriptions) -----

    /** Larger than spring.servlet.multipart.max-file-size (2 MB, about 30 s of speech in the browser's format). */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleRecordingTooLarge(MaxUploadSizeExceededException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.BAD_REQUEST, "The recording is too long. Please ask a shorter question (at most 30 seconds).",
                null, request);
    }

    /** The request is not a valid multipart/form-data upload. */
    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ApiResponse<Void>> handleBrokenUpload(MultipartException ex, HttpServletRequest request) {
        log.debug("Unreadable upload: {}", ex.getMessage());
        return buildResponse(HttpStatus.BAD_REQUEST,
                "The upload could not be read: send the recording as multipart/form-data in the field 'audio'", null, request);
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingPart(MissingServletRequestPartException ex, HttpServletRequest request) {
        String message = "Required file '%s' is missing".formatted(ex.getRequestPartName());
        return buildResponse(HttpStatus.BAD_REQUEST, message, null, request);
    }

    /** For example JSON sent to /transcriptions, which needs multipart/form-data. */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleWrongContentType(HttpMediaTypeNotSupportedException ex, HttpServletRequest request) {
        String message = "Content type '%s' is not supported here; use %s".formatted(ex.getContentType(), ex.getSupportedMediaTypes());
        return buildResponse(HttpStatus.UNSUPPORTED_MEDIA_TYPE, message, null, request);
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

    // ----- database busy -----

    /**
     * No database connection: none became free within 5 s (spring.datasource.hikari.connection-timeout), or MySQL did not
     * answer. CannotCreateTransactionException comes from a transaction; DataAccessResourceFailureException (e.g.
     * CannotGetJdbcConnectionException) from a query outside one, like the conversation memory's JdbcTemplate.
     * 503 tells the client to try again; the request did not change anything.
     */
    @ExceptionHandler({CannotCreateTransactionException.class, DataAccessResourceFailureException.class})
    public ResponseEntity<ApiResponse<Void>> handleDatabaseBusy(NestedRuntimeException ex, HttpServletRequest request) {
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
