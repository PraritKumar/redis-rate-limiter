package com.prarit.ratelimiter.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when a client exceeds their rate limit.
 * Maps to HTTP 429 Too Many Requests.
 *
 * HTTP 429 is the industry-standard status code for rate limiting.
 * Every major API (GitHub, Stripe, Twitter) uses 429.
 */
@ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
public class RateLimitExceededException extends RuntimeException {

    public RateLimitExceededException(String message) {
        super(message);
    }
}
