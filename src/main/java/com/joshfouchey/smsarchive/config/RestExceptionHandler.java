package com.joshfouchey.smsarchive.config;

import com.joshfouchey.smsarchive.exception.JobAlreadyRunningException;
import com.joshfouchey.smsarchive.exception.ResourceNotFoundException;
import com.joshfouchey.smsarchive.exception.ResourceOwnershipException;
import com.joshfouchey.smsarchive.exception.UnauthenticatedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;
import java.util.Map;

@ControllerAdvice
public class RestExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(RestExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String,Object>> handleNotFound(ResourceNotFoundException ex, WebRequest req) {
        return error(HttpStatus.NOT_FOUND, ex.getMessage(), req);
    }

    @ExceptionHandler(ResourceOwnershipException.class)
    public ResponseEntity<Map<String,Object>> handleOwnership(ResourceOwnershipException ex, WebRequest req) {
        return error(HttpStatus.FORBIDDEN, ex.getMessage(), req);
    }

    @ExceptionHandler(UnauthenticatedException.class)
    public ResponseEntity<Map<String,Object>> handleUnauthenticated(UnauthenticatedException ex, WebRequest req) {
        return error(HttpStatus.UNAUTHORIZED, ex.getMessage(), req);
    }

    @ExceptionHandler(JobAlreadyRunningException.class)
    public ResponseEntity<Map<String,Object>> handleJobRunning(JobAlreadyRunningException ex, WebRequest req) {
        return error(HttpStatus.CONFLICT, ex.getMessage(), req);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String,Object>> handleIllegalArgument(IllegalArgumentException ex, WebRequest req) {
        return error(HttpStatus.BAD_REQUEST, ex.getMessage(), req);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String,Object>> handleNoResource(NoResourceFoundException ex, WebRequest req) {
        log.debug("No resource for {}", req.getDescription(false));
        return error(HttpStatus.NOT_FOUND, "Resource not found", req);
    }

    // Not ErrorResponse implementations, so the generic handler can't read a status from them
    @ExceptionHandler(TypeMismatchException.class)
    public ResponseEntity<Map<String,Object>> handleTypeMismatch(TypeMismatchException ex, WebRequest req) {
        log.debug("Type mismatch on {}: {}", req.getDescription(false), ex.getMessage());
        String name = ex.getPropertyName() != null ? ex.getPropertyName() : "parameter";
        return error(HttpStatus.BAD_REQUEST, "Invalid value for '" + name + "'", req);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String,Object>> handleUnreadable(HttpMessageNotReadableException ex, WebRequest req) {
        log.debug("Unreadable request body on {}: {}", req.getDescription(false), ex.getMessage());
        return error(HttpStatus.BAD_REQUEST, "Malformed request body", req);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String,Object>> handleGeneric(Exception ex, WebRequest req) {
        // Spring MVC's own exceptions (missing param, type mismatch, bad JSON, wrong method, ...) carry their status
        if (ex instanceof ErrorResponse er && er.getStatusCode().is4xxClientError()) {
            log.debug("Client error {} on {}: {}", er.getStatusCode().value(), req.getDescription(false), ex.getMessage());
            String detail = er.getBody().getDetail();
            return error(er.getStatusCode(), detail != null ? detail : "Bad request", req, er.getHeaders());
        }
        log.error("Unhandled exception on {}", req.getDescription(false), ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred", req);
    }

    private ResponseEntity<Map<String,Object>> error(HttpStatus status, String message, WebRequest req) {
        return error(status, message, req, HttpHeaders.EMPTY);
    }

    private ResponseEntity<Map<String,Object>> error(HttpStatusCode status, String message, WebRequest req, HttpHeaders headers) {
        HttpStatus resolved = HttpStatus.resolve(status.value());
        Map<String,Object> body = Map.of(
                "timestamp", Instant.now().toString(),
                "status", status.value(),
                "error", resolved != null ? resolved.getReasonPhrase() : String.valueOf(status.value()),
                "message", message,
                "path", req.getDescription(false).replace("uri=", "")
        );
        return ResponseEntity.status(status).headers(headers).body(body);
    }
}
