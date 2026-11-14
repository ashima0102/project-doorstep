package com.azentio.doorstep.api;

import com.azentio.doorstep.api.dto.ApiError;
import com.azentio.doorstep.service.ConflictException;
import com.azentio.doorstep.service.InvalidRequestException;
import com.azentio.doorstep.service.NotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> onValidation(MethodArgumentNotValidException ex) {
        List<ApiError.FieldError> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ApiError.FieldError(fe.getField(), fe.getDefaultMessage()))
                .toList();
        return ResponseEntity.badRequest()
                .body(new ApiError(400, "Validation failed", errors));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> onUnreadable(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest().body(ApiError.of(400, "Malformed JSON request"));
    }

    @ExceptionHandler(NotFoundException.class)
    ResponseEntity<ApiError> onNotFound(NotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError.of(404, ex.getMessage()));
    }

    @ExceptionHandler(ConflictException.class)
    ResponseEntity<ApiError> onConflict(ConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError.of(409, ex.getMessage()));
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    ResponseEntity<ApiError> onMissingHeader(MissingRequestHeaderException ex) {
        return ResponseEntity.badRequest().body(ApiError.of(400, "Missing header " + ex.getHeaderName()));
    }

    @ExceptionHandler(InvalidRequestException.class)
    ResponseEntity<ApiError> onInvalid(InvalidRequestException ex) {
        return ResponseEntity.badRequest().body(ApiError.of(400, ex.getMessage()));
    }
}
