package com.chatroom.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * Equivalent to the try/catch blocks in every Node.js route handler.
 * Instead of writing:
 *   try { ... } catch { res.status(500).json({ error: "Server error" }) }
 * in every controller method, we handle all exceptions in one place.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    // Handles @Valid validation failures (e.g. missing required fields)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> handleValidation(MethodArgumentNotValidException ex) {
        String errors = ex.getBindingResult().getFieldErrors().stream()
            .map(FieldError::getDefaultMessage)
            .collect(Collectors.joining(", "));
        return ResponseEntity.badRequest().body(Map.of("error", errors));
    }

    // Handles all other unexpected errors
    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleGeneral(Exception ex) {
        System.err.println("Unhandled error: " + ex.getMessage());
        ex.printStackTrace();
        return ResponseEntity.status(500).body(Map.of("error", "Server error"));
    }
}
