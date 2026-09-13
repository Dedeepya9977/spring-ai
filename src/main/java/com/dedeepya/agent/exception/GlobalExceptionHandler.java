package com.dedeepya.agent.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class GlobalExceptionHandler {
  @ExceptionHandler(ApiException.class)
  ResponseEntity<ProblemDetail> api(ApiException ex, HttpServletRequest req) {
    return problem(ex.status(), ex.code(), ex.getMessage(), req);
  }

  @ExceptionHandler({
    MethodArgumentNotValidException.class,
    HttpMessageNotReadableException.class,
    IllegalArgumentException.class
  })
  ResponseEntity<ProblemDetail> invalid(Exception ex, HttpServletRequest req) {
    return problem(
        HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Invalid request fields or JSON", req);
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ProblemDetail> unexpected(Exception ex, HttpServletRequest req) {
    LoggerFactory.getLogger(getClass())
        .error("request_failed category={}", ex.getClass().getSimpleName());
    return problem(
        HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Request could not be completed", req);
  }

  private ResponseEntity<ProblemDetail> problem(
      HttpStatus status, String code, String detail, HttpServletRequest req) {
    ProblemDetail p = ProblemDetail.forStatusAndDetail(status, detail);
    p.setTitle(code);
    p.setProperty("code", code);
    p.setProperty("correlationId", req.getAttribute("correlationId"));
    return ResponseEntity.status(status).body(p);
  }
}
