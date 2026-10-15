package com.modeldock;

import java.util.Map;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class Errors {
  @ExceptionHandler(ApiException.class)
  ResponseEntity<?> api(ApiException e) {
    return ResponseEntity.status(e.status).body(Map.of("code", e.code, "message", e.getMessage()));
  }

  @ExceptionHandler({
    MethodArgumentNotValidException.class,
    HttpMessageNotReadableException.class,
    IllegalArgumentException.class,
    jakarta.validation.ConstraintViolationException.class,
    org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
    org.springframework.web.method.annotation.HandlerMethodValidationException.class
  })
  ResponseEntity<?> invalid(Exception e) {
    return ResponseEntity.badRequest()
        .body(Map.of("code", "INVALID_REQUEST", "message", "입력값과 허용된 요청 필드를 확인해 주세요."));
  }

  @ExceptionHandler(DuplicateKeyException.class)
  ResponseEntity<?> duplicate(Exception e) {
    return ResponseEntity.status(409)
        .body(Map.of("code", "CONFLICT", "message", "이미 등록되었거나 처리한 요청입니다."));
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<?> unexpected(Exception e) {
    return ResponseEntity.internalServerError()
        .body(Map.of("code", "INTERNAL_ERROR", "message", "요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요."));
  }
}
