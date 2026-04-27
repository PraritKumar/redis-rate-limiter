package com.prarit.ratelimiter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Redis Rate Limiter application.
 *
 * Key Spring Boot auto-configurations that activate here:
 *   - RedisAutoConfiguration   → sets up RedisTemplate bean
 *   - AopAutoConfiguration     → enables @Aspect processing
 *   - WebMvcAutoConfiguration  → sets up Tomcat + MVC
 */
@SpringBootApplication
public class RateLimiterApplication {

    public static void main(String[] args) {
        SpringApplication.run(RateLimiterApplication.class, args);
    }
}
