package com.prarit.ratelimiter.aspect;

import com.prarit.ratelimiter.annotation.RateLimit;
import com.prarit.ratelimiter.exception.RateLimitExceededException;
import com.prarit.ratelimiter.service.RateLimiterService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;

/**
 * THE AOP INTERCEPTOR — The most senior-level concept in this project.
 *
 * WHAT IS AOP (Aspect-Oriented Programming)?
 *
 * AOP lets you run code AROUND method calls without modifying those methods.
 * It's how Spring implements @Transactional, @Cacheable, Spring Security — all of it.
 *
 * PROBLEM AOP SOLVES:
 * Imagine you have 20 API endpoints and need rate limiting on all of them.
 * Without AOP, you'd copy-paste rateLimiterService.check() into every method.
 * That's 20 places to maintain. Change the logic → change 20 files.
 *
 * With AOP:
 *   - Rate limit logic lives in ONE place (this file)
 *   - Any method with @RateLimit gets it automatically
 *   - Remove the annotation → rate limiting removed. Zero other changes.
 *
 * KEY AOP TERMS:
 *
 * @Aspect       → "This class contains cross-cutting logic (not business logic)"
 *
 * Advice        → The code that runs. Types:
 *                 @Before    → runs before the method
 *                 @After     → runs after the method
 *                 @Around    → wraps the method (we use this — most powerful)
 *
 * Pointcut      → The expression that defines WHICH methods to intercept.
 *                 "@annotation(com.prarit.ratelimiter.annotation.RateLimit)"
 *                 = "intercept any method annotated with @RateLimit"
 *
 * JoinPoint     → The actual method call being intercepted.
 *                 ProceedingJoinPoint lets us call proceed() to actually
 *                 execute the original method.
 *
 * HOW IT WORKS AT RUNTIME:
 * 1. Spring scans all beans at startup
 * 2. Finds methods annotated with @RateLimit
 * 3. Wraps those methods with a dynamic proxy
 * 4. Every call to those methods goes through this Aspect first
 * 5. We check the rate limit → allow or throw 429
 */
@Aspect         // "I am an AOP aspect"
@Component      // "Register me as a Spring bean"
@RequiredArgsConstructor
@Slf4j
public class RateLimitAspect {

    private final RateLimiterService rateLimiterService;

    /**
     * @Around advice — intercepts method calls matching the pointcut.
     *
     * @Around = we control WHEN (and whether) the original method runs.
     * We must call joinPoint.proceed() to actually execute the method.
     * If we don't call proceed(), the method never runs.
     *
     * Pointcut: "@annotation(rateLimit)" matches any method with @RateLimit
     * The "rateLimit" parameter gives us direct access to the annotation values.
     */
    @Around("@annotation(rateLimit)")
    public Object checkRateLimit(ProceedingJoinPoint joinPoint, RateLimit rateLimit) throws Throwable {

        // ── STEP 1: Get the client's IP address ───────────────────────────────
        // This is the "client identifier" — what we track per-client in Redis.
        // In production you might use user ID from JWT token instead of IP.
        String clientIp = getClientIpAddress();

        // ── STEP 2: Build a unique key per client + endpoint ─────────────────
        // Format: "192.168.1.1:/api/public"
        // Why include the endpoint? So each endpoint has its OWN rate limit counter.
        // A client hitting /api/public doesn't consume their /api/premium quota.
        String methodName = joinPoint.getSignature().getName();
        String clientId = clientIp + ":" + methodName;

        int limit = rateLimit.limit();
        int windowSeconds = rateLimit.windowSeconds();

        log.debug("Rate limit check — clientId: {}, limit: {}/{}", clientId, limit, windowSeconds);

        // ── STEP 3: Check the rate limit ──────────────────────────────────────
        boolean allowed = rateLimiterService.isAllowed(clientId, limit, windowSeconds);

        if (!allowed) {
            // Throw our custom exception → GlobalExceptionHandler returns 429
            throw new RateLimitExceededException(
                String.format("Rate limit exceeded: %d requests per %d seconds", limit, windowSeconds)
            );
        }

        // ── STEP 4: Allow the request through ────────────────────────────────
        // joinPoint.proceed() calls the ORIGINAL controller method.
        // Whatever it returns, we return too.
        return joinPoint.proceed();
    }

    /**
     * Extracts the client's real IP address from the HTTP request.
     *
     * WHY NOT JUST USE request.getRemoteAddr()?
     * In production, your app sits behind a load balancer or reverse proxy.
     * The proxy's IP would be returned, not the client's real IP.
     *
     * The real IP is forwarded in the X-Forwarded-For header.
     * We check that first, then fall back to the direct address.
     */
    private String getClientIpAddress() {
        try {
            ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
            HttpServletRequest request = attributes.getRequest();

            // X-Forwarded-For contains the original client IP when behind a proxy
            String xForwardedFor = request.getHeader("X-Forwarded-For");
            if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
                // X-Forwarded-For can be: "clientIP, proxy1IP, proxy2IP"
                // We only want the first one — the original client
                return xForwardedFor.split(",")[0].trim();
            }

            return request.getRemoteAddr();

        } catch (Exception e) {
            log.warn("Could not extract client IP, using fallback");
            return "unknown";
        }
    }
}
