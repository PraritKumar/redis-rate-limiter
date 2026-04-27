# Redis Rate Limiter

A production-grade distributed rate limiter built with **Java 21**, **Spring Boot 3**, **Redis**, and **AOP**. Implements the **Sliding Window algorithm** to enforce per-client request limits across multiple application instances.

---

## Why Redis (Not In-Memory)?

```
❌ In-memory — broken at scale:
   Instance 1: client made 90 requests → allows more
   Instance 2: client made 90 requests → allows more (doesn't know about Instance 1)
   Reality: client made 180 requests. Limit bypassed.

✅ Redis-backed — works at any scale:
   Instance 1: checks Redis → 90 requests
   Instance 2: checks Redis → 90 requests (same shared counter)
   Reality: limit correctly enforced across ALL instances
```

---

## Algorithm — Sliding Window

```
Limit: 5 requests per 10 seconds

Time →   0    2    4    6    8    10   12
         r1   r2   r3   r4   r5        r6

At r6 (t=12): window = [t=2 to t=12]
  r1 (t=0) expired → removed. Remaining: 4 requests.
  4 < 5 → ALLOWED ✅

If r6 came at t=9: all 5 still in window → BLOCKED ❌ HTTP 429
```

**Redis implementation — 4 commands per request:**
```bash
ZREMRANGEBYSCORE key 0 {windowStart}  # remove expired requests
ZADD key {now} {requestId}            # record this request
EXPIRE key {windowSeconds}            # auto-cleanup idle keys
ZCARD key                             # count → allow or block
```

---

## Architecture

```
HTTP Request
     │
     ▼
RateLimitAspect (AOP @Around)
     ├── reads @RateLimit annotation values
     ├── extracts client IP
     ├── calls RateLimiterService.isAllowed()
     │        └── Redis Sorted Set operations
     ├── blocked → GlobalExceptionHandler → HTTP 429
     └── allowed → proceed() → Controller runs
```

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.2 |
| Rate Store | Redis 7 (Sorted Sets) |
| Redis Client | Lettuce (non-blocking) |
| Cross-cutting | Spring AOP (AspectJ) |
| Containerisation | Docker, Docker Compose |
| Testing | JUnit 5, Mockito |

---

## Endpoints

| Method | Endpoint | Rate Limit | Purpose |
|--------|----------|-----------|---------|
| GET | `/api/public` | 100 req/60s | Anonymous users |
| GET | `/api/standard` | 10 req/60s | Hit 11x fast to see 429 |
| GET | `/api/strict` | 3 req/30s | Payment/OTP simulation |
| GET | `/api/health` | None | Health check |

---

## Running

```bash
docker-compose up --build
# Swagger UI: http://localhost:8080/swagger-ui.html
```

## Testing Rate Limits

```bash
for i in {1..4}; do
  echo "Request $i: $(curl -s -o /dev/null -w '%{http_code}' http://localhost:8080/api/strict)"
done
# Request 1: 200
# Request 2: 200
# Request 3: 200
# Request 4: 429
```

## How @RateLimit Works

```java
// Controller is completely clean — no rate limit code:
@GetMapping("/api/strict")
@RateLimit(limit = 3, windowSeconds = 30)  // ← AOP intercepts this
public ResponseEntity<?> strictEndpoint() {
    return ResponseEntity.ok("success");
}
// RateLimitAspect runs BEFORE this method on every request.
```

## Running Tests

```bash
./mvnw test
```
