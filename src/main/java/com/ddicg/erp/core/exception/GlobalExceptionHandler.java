package com.ddicg.erp.core.exception;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Global exception handler for REST APIs.
 * <p>
 * Converts all exceptions into consistent {@link Response} envelopes
 * and delegates {@link BusinessException} to RFC 7807 Problem Details
 * for richer error payloads.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // ========== BusinessException ==========

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ProblemDetail> handleBusinessException(BusinessException ex) {
        log.warn("Business exception: [{}] {}", ex.getCode(), ex.getDetail());

        ProblemDetail problem = ProblemDetail.forStatus(ex.getHttpStatus());
        problem.setType(ex.getType());
        problem.setTitle(ex.getTitle());
        problem.setDetail(ex.getDetail());
        problem.setProperty("errorCode", ex.getCode());

        if (ex.hasProperties()) {
            problem.setProperty("details", ex.getProperties());
        }

        return ResponseEntity.status(ex.getHttpStatus())
                .body(problem);
    }

    // ========== Validation Errors (@Valid) ==========

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        log.warn("Validation failed: {}", ex.getMessage());

        Map<String, String> fieldErrors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        fieldError -> fieldError.getDefaultMessage() != null
                                ? fieldError.getDefaultMessage()
                                : "Invalid value",
                        (a, b) -> b  // keep last if duplicates
                ));

        ProblemDetail problem = ProblemDetail.forStatus(status);
        problem.setType(URI.create("about:blank"));
        problem.setTitle("Validation Failed");
        problem.setDetail("One or more fields are invalid.");
        problem.setProperty("errorCode", "VALIDATION_FAILED");
        problem.setProperty("fieldErrors", fieldErrors);

        return ResponseEntity.status(status).body(problem);
    }

    // ========== Constraint Violation (@PathVariable, @RequestParam) ==========

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> handleConstraintViolation(ConstraintViolationException ex) {
        log.warn("Constraint violation: {}", ex.getMessage());

        Map<String, String> violations = ex.getConstraintViolations()
                .stream()
                .collect(Collectors.toMap(
                        cv -> cv.getPropertyPath().toString(),
                        ConstraintViolation::getMessage,
                        (a, b) -> b
                ));

        ProblemDetail problem = ProblemDetail.forStatus(org.springframework.http.HttpStatus.BAD_REQUEST);
        problem.setType(URI.create("about:blank"));
        problem.setTitle("Constraint Violation");
        problem.setDetail("Invalid request parameters.");
        problem.setProperty("errorCode", "VALIDATION_FAILED");
        problem.setProperty("fieldErrors", violations);

        return ResponseEntity.badRequest().body(problem);
    }

    // ========== Access Denied ==========

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException ex) {
        log.warn("Access denied: {}", ex.getMessage());

        ProblemDetail problem = ProblemDetail.forStatus(
                org.springframework.http.HttpStatus.FORBIDDEN);
        problem.setType(URI.create("about:blank"));
        problem.setTitle("Access Denied");
        problem.setDetail("You do not have permission to perform this action.");
        problem.setProperty("errorCode", "ACCESS_DENIED");

        return ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN)
                .body(problem);
    }

    // ========== Fallback for unhandled exceptions ==========

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnhandled(Exception ex) {
        log.error("Unhandled exception: {}", ex.getMessage(), ex);

        ProblemDetail problem = ProblemDetail.forStatus(
                org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR);
        problem.setType(URI.create("about:blank"));
        problem.setTitle("Internal Server Error");
        problem.setDetail("An unexpected error occurred. Please try again later.");
        problem.setProperty("errorCode", "INTERNAL_ERROR");

        return ResponseEntity.status(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR)
                .body(problem);
    }
}
