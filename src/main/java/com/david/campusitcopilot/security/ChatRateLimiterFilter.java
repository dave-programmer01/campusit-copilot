package com.david.campusitcopilot.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Per-IP rate limiter for the public {@code /chat} endpoint.
 * Protects downstream LLM endpoints from abuse / bill run-up by enforcing a token-bucket limit per client IP.
 */
@Component
@Order(1)
public class ChatRateLimiterFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ChatRateLimiterFilter.class);
    public static final int HTTP_TOO_MANY_REQUESTS = 429;

    private final boolean enabled;
    private final double capacity;
    private final double refillTokens;
    private final long refillDurationSeconds;
    private final LongSupplier nanoTimeSupplier;
    private final ConcurrentHashMap<String, TokenBucket> buckets = new ConcurrentHashMap<>();

    @Autowired
    public ChatRateLimiterFilter(
            @Value("${app.rate-limit.enabled:true}") boolean enabled,
            @Value("${app.rate-limit.chat.capacity:20}") double capacity,
            @Value("${app.rate-limit.chat.refill-tokens:20}") double refillTokens,
            @Value("${app.rate-limit.chat.refill-duration-seconds:60}") long refillDurationSeconds) {
        this(enabled, capacity, refillTokens, refillDurationSeconds, System::nanoTime);
    }

    public ChatRateLimiterFilter(
            boolean enabled,
            double capacity,
            double refillTokens,
            long refillDurationSeconds,
            LongSupplier nanoTimeSupplier) {
        this.enabled = enabled;
        this.capacity = capacity;
        this.refillTokens = refillTokens;
        this.refillDurationSeconds = refillDurationSeconds;
        this.nanoTimeSupplier = nanoTimeSupplier != null ? nanoTimeSupplier : System::nanoTime;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        if (!enabled || !isChatRequest(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        String ip = extractClientIp(request);
        TokenBucket bucket = buckets.computeIfAbsent(
                ip,
                k -> new TokenBucket(capacity, refillTokens, refillDurationSeconds, nanoTimeSupplier)
        );

        long nowNanos = nanoTimeSupplier.getAsLong();
        boolean allowed = bucket.tryConsume(nowNanos);

        if (allowed) {
            response.setHeader("X-RateLimit-Limit", String.valueOf((long) capacity));
            response.setHeader("X-RateLimit-Remaining", String.valueOf((long) Math.max(0, bucket.getAvailableTokens(nowNanos))));
            filterChain.doFilter(request, response);
        } else {
            long waitSeconds = bucket.getEstimatedWaitSeconds(nowNanos);
            log.warn("Rate limit exceeded for IP: {} on /chat (retry after {}s)", ip, waitSeconds);
            response.setStatus(HTTP_TOO_MANY_REQUESTS);
            response.setHeader("Retry-After", String.valueOf(waitSeconds));
            response.setHeader("X-RateLimit-Limit", String.valueOf((long) capacity));
            response.setHeader("X-RateLimit-Remaining", "0");
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("{\"error\":\"Too Many Requests\",\"message\":\"Rate limit exceeded. Please try again later.\"}");
            response.getWriter().flush();
        }

        // Periodic maintenance to prevent memory leaks in long-running instances
        if (buckets.size() > 5000) {
            pruneStaleBuckets(nowNanos);
        }
    }

    private boolean isChatRequest(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path != null && (path.equals("/chat") || path.startsWith("/chat/"));
    }

    public static String extractClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(xForwardedFor)) {
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (StringUtils.hasText(xRealIp)) {
            return xRealIp.trim();
        }
        return request.getRemoteAddr() != null ? request.getRemoteAddr() : "unknown";
    }

    private void pruneStaleBuckets(long nowNanos) {
        buckets.entrySet().removeIf(entry -> {
            TokenBucket b = entry.getValue();
            return b.getAvailableTokens(nowNanos) >= capacity;
        });
    }

    public void reset() {
        buckets.clear();
    }

    public int getTrackedIpCount() {
        return buckets.size();
    }

    /**
     * Thread-safe in-memory Token Bucket rate limiter.
     */
    public static class TokenBucket {
        private final double capacity;
        private final double refillRatePerNanos;
        private double availableTokens;
        private long lastRefillNanos;

        public TokenBucket(double capacity, double refillTokens, long refillDurationSeconds, LongSupplier nanoSupplier) {
            this.capacity = capacity;
            this.refillRatePerNanos = refillTokens / (Math.max(1, refillDurationSeconds) * 1_000_000_000.0);
            this.availableTokens = capacity;
            this.lastRefillNanos = nanoSupplier.getAsLong();
        }

        public synchronized boolean tryConsume(long nowNanos) {
            refill(nowNanos);
            if (availableTokens >= 1.0) {
                availableTokens -= 1.0;
                return true;
            }
            return false;
        }

        public synchronized double getAvailableTokens(long nowNanos) {
            refill(nowNanos);
            return availableTokens;
        }

        public synchronized long getEstimatedWaitSeconds(long nowNanos) {
            refill(nowNanos);
            if (availableTokens >= 1.0) {
                return 0;
            }
            double needed = 1.0 - availableTokens;
            return Math.max(1, (long) Math.ceil(needed / (refillRatePerNanos * 1_000_000_000.0)));
        }

        private void refill(long nowNanos) {
            long elapsedNanos = Math.max(0, nowNanos - lastRefillNanos);
            if (elapsedNanos > 0) {
                double tokensToAdd = elapsedNanos * refillRatePerNanos;
                availableTokens = Math.min(capacity, availableTokens + tokensToAdd);
                lastRefillNanos = nowNanos;
            }
        }
    }
}
