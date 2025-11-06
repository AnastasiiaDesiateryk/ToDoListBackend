package com.example.todo.exception;

import com.example.todo.security.FirebaseIdTokenVerifier;
import com.example.todo.service.TaskService.PreconditionFailedException;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class RestExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Object> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, Object> body = basic(HttpStatus.BAD_REQUEST, "Validation failed", "Request validation error");
        Map<String, String> errors = new HashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            errors.put(fe.getField(), fe.getDefaultMessage());
        }
        body.put("errors", errors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<Object> handleNotFound(EntityNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(basic(HttpStatus.NOT_FOUND, "Not Found", ex.getMessage()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Object> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(basic(HttpStatus.FORBIDDEN, "Forbidden", ex.getMessage()));
    }

    // было:
    // @ExceptionHandler({IllegalArgumentException.class, FirebaseIdTokenVerifier.InvalidToken.class})
    // public ResponseEntity<Object> handleBadRequest(RuntimeException ex) { ... 400 ... }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Object> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(basic(HttpStatus.BAD_REQUEST, "Bad Request", ex.getMessage()));
    }

    // Firebase ID token невалиден/просрочен → 401
    @ExceptionHandler(FirebaseIdTokenVerifier.InvalidToken.class)
    public ResponseEntity<Object> handleInvalidToken(FirebaseIdTokenVerifier.InvalidToken ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED) // 401
                // по желанию — добавить стандартный заголовок
                // .header(HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"invalid_token\"")
                .body(basic(HttpStatus.UNAUTHORIZED, "Unauthorized", "Invalid or expired ID token"));
    }


    @ExceptionHandler(PreconditionFailedException.class)
    public ResponseEntity<Object> handlePrecondition(PreconditionFailedException ex) {
        return ResponseEntity.status(HttpStatus.PRECONDITION_FAILED)
                .body(basic(HttpStatus.PRECONDITION_FAILED, "Precondition Failed", ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleAll(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(basic(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error", ex.getMessage()));
    }

    private Map<String, Object> basic(HttpStatus status, String error, String message) {
        Map<String, Object> m = new HashMap<>();
        m.put("timestamp", OffsetDateTime.now().toString());
        m.put("status", status.value());
        m.put("error", error);
        m.put("message", message);
        return m;
    }
}
