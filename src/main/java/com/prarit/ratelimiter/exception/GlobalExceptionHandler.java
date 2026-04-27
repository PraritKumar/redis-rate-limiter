package com.prarit.ratelimiter.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Global exception handler — same pattern as Project 1.
 *
 * New addition: Rate limit response includes industry-standard headers
 * so clients know when to retry.
 *
 * STANDARD RATE LIMIT HEADERS (used by GitHub, Stripe, etc.):
 *   X-RateLimit-Limit     → max requests allowed
 *   X-RateLimit-Remaining → requests left in window
 *   Retry-After           → seconds until window resets
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handles rate limit exceeded → HTTP 429 with Retry-After header.
     *
     * The Retry-After header tells clients exactly when to retry.
     * This is the production standard — don't make clients guess.
     */
    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<Map<String, Object>> handleRateLimitExceeded(
            RateLimitExceededException ex) {

        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", 429);
        body.put("error", "Too Many Requests");
        body.put("message", ex.getMessage());
        body.put("retryAfter", "Please wait before making more requests");

        return ResponseEntity
                .status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", "60")          // standard HTTP header
                .header("X-RateLimit-Limit", "see message")
                .body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex) {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", 500);
        body.put("error", "Internal Server Error");
        body.put("message", ex.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
