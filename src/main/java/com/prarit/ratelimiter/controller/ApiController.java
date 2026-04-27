package com.prarit.ratelimiter.controller;

import com.prarit.ratelimiter.annotation.RateLimit;
import com.prarit.ratelimiter.service.RateLimiterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * DEMO CONTROLLER — 3 endpoints with different rate limits.
 *
 * This demonstrates the power of the @RateLimit annotation:
 * - Each endpoint has a DIFFERENT limit
 * - The controller method itself has zero rate-limit code
 * - All the work happens invisibly in RateLimitAspect
 *
 * Think of it like real-world API tiers:
 *   /public   → generous limit   (anonymous users)
 *   /standard → normal limit     (registered users)
 *   /premium  → strict limit     (high-value paid users get more)
 *
 * Notice: the controller methods look completely clean.
 * No rateLimiterService calls. No if/else. Just business logic.
 * The AOP handles everything else.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Rate Limited API", description = "Endpoints demonstrating Redis-based rate limiting")
public class ApiController {

    private final RateLimiterService rateLimiterService;

    /**
     * Public endpoint — generous limit.
     * 100 requests per 60 seconds.
     * Rate limit is enforced by @RateLimit — zero code in the method body.
     */
    @GetMapping("/public")
    @RateLimit(limit = 100, windowSeconds = 60)
    @Operation(
        summary = "Public endpoint",
        description = "Rate limit: 100 requests per 60 seconds. Easy to test."
    )
    public ResponseEntity<Map<String, Object>> publicEndpoint(HttpServletRequest request) {
        Map<String, Object> response = new HashMap<>();
        response.put("endpoint", "public");
        response.put("message", "This endpoint allows 100 requests/minute");
        response.put("timestamp", LocalDateTime.now());
        response.put("clientIp", getClientIp(request));

        // Show the client their current usage
        String clientIp = getClientIp(request);
        long currentCount = rateLimiterService.getCurrentCount(
                clientIp + ":publicEndpoint", 60);
        response.put("yourRequestCount", currentCount);
        response.put("limit", 100);
        response.put("remaining", Math.max(0, 100 - currentCount));

        return ResponseEntity.ok(response);
    }

    /**
     * Standard endpoint — moderate limit.
     * 10 requests per 60 seconds.
     * Easy to hit during testing — try rapid-fire requests to see 429.
     */
    @GetMapping("/standard")
    @RateLimit(limit = 10, windowSeconds = 60)
    @Operation(
        summary = "Standard endpoint",
        description = "Rate limit: 10 requests per 60 seconds. Hit this 11 times fast to see 429."
    )
    public ResponseEntity<Map<String, Object>> standardEndpoint(HttpServletRequest request) {
        Map<String, Object> response = new HashMap<>();
        response.put("endpoint", "standard");
        response.put("message", "This endpoint allows 10 requests/minute");
        response.put("timestamp", LocalDateTime.now());
        response.put("clientIp", getClientIp(request));

        String clientIp = getClientIp(request);
        long currentCount = rateLimiterService.getCurrentCount(
                clientIp + ":standardEndpoint", 60);
        response.put("yourRequestCount", currentCount);
        response.put("limit", 10);
        response.put("remaining", Math.max(0, 10 - currentCount));

        return ResponseEntity.ok(response);
    }

    /**
     * Strict endpoint — very tight limit.
     * 3 requests per 30 seconds.
     * Simulates a premium action like a payment API or OTP request.
     * Makes it trivial to demo rate limiting during a walkthrough.
     */
    @GetMapping("/strict")
    @RateLimit(limit = 3, windowSeconds = 30)
    @Operation(
        summary = "Strict endpoint",
        description = "Rate limit: 3 requests per 30 seconds. Hit it 4 times to see 429 instantly."
    )
    public ResponseEntity<Map<String, Object>> strictEndpoint(HttpServletRequest request) {
        Map<String, Object> response = new HashMap<>();
        response.put("endpoint", "strict");
        response.put("message", "This endpoint allows only 3 requests per 30 seconds");
        response.put("timestamp", LocalDateTime.now());
        response.put("clientIp", getClientIp(request));

        String clientIp = getClientIp(request);
        long currentCount = rateLimiterService.getCurrentCount(
                clientIp + ":strictEndpoint", 30);
        response.put("yourRequestCount", currentCount);
        response.put("limit", 3);
        response.put("remaining", Math.max(0, 3 - currentCount));

        return ResponseEntity.ok(response);
    }

    /**
     * Health check endpoint — NO rate limiting.
     * Used by Docker/Kubernetes health probes.
     * Notice: no @RateLimit annotation → Aspect never intercepts this.
     */
    @GetMapping("/health")
    @Operation(summary = "Health check — no rate limit")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "UP");
        response.put("timestamp", LocalDateTime.now());
        response.put("service", "redis-rate-limiter");
        return ResponseEntity.ok(response);
    }

    // ── Helper ───────────────────────────────────────────────────────────────

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
