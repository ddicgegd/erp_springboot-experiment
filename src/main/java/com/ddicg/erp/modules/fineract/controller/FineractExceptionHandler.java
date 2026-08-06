package com.ddicg.erp.modules.fineract.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientResponseException;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice(basePackages = "com.ddicg.erp.fineract.controller")
public class FineractExceptionHandler {

    @ExceptionHandler(RestClientResponseException.class)
    public ResponseEntity<Map<String, Object>> handleRestClientResponseException(RestClientResponseException ex) {
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("status", "error");
        errorResponse.put("httpStatusCode", ex.getStatusCode().value());
        
        try {
            // Forward the raw Fineract body for easier debugging while hiding internal stacktraces
            errorResponse.put("fineractResponse", ex.getResponseBodyAsString());
            errorResponse.put("message", "Lỗi từ hệ thống Core Banking (Fineract).");
        } catch (Exception e) {
            errorResponse.put("message", "Lỗi kết nối Fineract: " + ex.getMessage());
        }

        return ResponseEntity.status(ex.getStatusCode()).body(errorResponse);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, Object> errors = new HashMap<>();
        errors.put("status", "error");
        errors.put("message", "Dữ liệu đầu vào không hợp lệ");
        
        Map<String, String> fieldErrors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error -> 
            fieldErrors.put(error.getField(), error.getDefaultMessage())
        );
        errors.put("fieldErrors", fieldErrors);
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errors);
    }
    
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex) {
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("status", "error");
        errorResponse.put("message", "Lỗi hệ thống nội bộ ERP: " + ex.getMessage());
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }
}
