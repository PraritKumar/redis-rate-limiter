package com.prarit.ratelimiter.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * CUSTOM ANNOTATION — @RateLimit
 *
 * WHAT IS AN ANNOTATION?
 * An annotation is metadata you attach to a method, class, or field.
 * By itself it does nothing — it's just a label.
 * The power comes from something READING that label and acting on it.
 * That "something" is our RateLimitAspect.
 *
 * USAGE:
 *   @RateLimit(limit = 10, windowSeconds = 60)
 *   @GetMapping("/api/data")
 *   public String getData() { ... }
 *
 * This means: "Allow max 10 requests per 60 seconds per client IP"
 *
 * META-ANNOTATIONS EXPLAINED:
 *
 * @Target(ElementType.METHOD)
 *   → This annotation can only be placed on methods.
 *     Putting it on a class or field = compile error.
 *
 * @Retention(RetentionPolicy.RUNTIME)
 *   → Keep this annotation at runtime (not just compile time).
 *     CRITICAL: Without RUNTIME retention, our Aspect can't read it.
 *     The JVM would strip it out before our code runs.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    /**
     * Maximum number of requests allowed within the time window.
     * Default: 100 requests
     */
    int limit() default 100;

    /**
     * Time window duration in seconds.
     * Default: 60 seconds (1 minute)
     */
    int windowSeconds() default 60;
}
