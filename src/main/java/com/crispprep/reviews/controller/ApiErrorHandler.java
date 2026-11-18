package com.crispprep.reviews.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponse;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class ApiErrorHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiErrorHandler.class);

    private ResponseEntity<Map<String, Object>> error(HttpStatusCode status, String message, Map<String, String> fields) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status.value());
        body.put("message", message);
        if (fields != null && !fields.isEmpty()) body.put("fields", fields);
        return ResponseEntity.status(status).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> validation(MethodArgumentNotValidException e) {
        Map<String, String> fields = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors()
                .forEach(f -> fields.put(f.getField(), f.getDefaultMessage()));
        return error(HttpStatus.BAD_REQUEST, "Validation failed", fields);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> badJson(HttpMessageNotReadableException e) {
        return error(HttpStatus.BAD_REQUEST, "Request body is missing or not valid JSON", null);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> status(ResponseStatusException e) {
        return error(e.getStatusCode(), e.getReason(), null);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> conflict(DataIntegrityViolationException e) {
        return error(HttpStatus.CONFLICT, "This record already exists", null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> unexpected(Exception e) {
        // Spring's own errors (unknown URL = 404, wrong HTTP method = 405 ...) keep their real status
        if (e instanceof ErrorResponse er) {
            HttpStatusCode code = er.getStatusCode();
            return error(code, code.value() == 404 ? "Not found" : er.getBody().getDetail(), null);
        }
        log.error("Unexpected error", e);   // full cause goes to the server log, never to the user
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong", null);
    }
}