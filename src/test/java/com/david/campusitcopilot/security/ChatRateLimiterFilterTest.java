package com.david.campusitcopilot.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ChatRateLimiterFilterTest {

    private AtomicLong fakeNanoTime;
    private ChatRateLimiterFilter rateLimiter;
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        fakeNanoTime = new AtomicLong(1_000_000_000L); // 1s
        // 3 requests capacity, refill 3 requests per 60s
        rateLimiter = new ChatRateLimiterFilter(true, 3.0, 3.0, 60L, fakeNanoTime::get);
        filterChain = mock(FilterChain.class);
    }

    @Test
    void testRequestsWithinCapacityPassAndSetHeaders() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/chat");
        request.setRemoteAddr("192.168.1.10");
        MockHttpServletResponse response = new MockHttpServletResponse();

        rateLimiter.doFilter(request, response, filterChain);

        assertEquals(200, response.getStatus());
        assertEquals("3", response.getHeader("X-RateLimit-Limit"));
        assertEquals("2", response.getHeader("X-RateLimit-Remaining"));
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void testExceedingCapacityReturns429TooManyRequests() throws ServletException, IOException {
        String ip = "192.168.1.20";

        // Consume 3 allowed tokens
        for (int i = 0; i < 3; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/chat");
            req.setRemoteAddr(ip);
            MockHttpServletResponse res = new MockHttpServletResponse();
            rateLimiter.doFilter(req, res, filterChain);
            assertEquals(200, res.getStatus());
        }

        // 4th request must be rejected with 429
        MockHttpServletRequest blockedReq = new MockHttpServletRequest("POST", "/chat");
        blockedReq.setRemoteAddr(ip);
        MockHttpServletResponse blockedRes = new MockHttpServletResponse();

        rateLimiter.doFilter(blockedReq, blockedRes, filterChain);

        assertEquals(429, blockedRes.getStatus());
        assertNotNull(blockedRes.getHeader("Retry-After"));
        assertEquals("3", blockedRes.getHeader("X-RateLimit-Limit"));
        assertEquals("0", blockedRes.getHeader("X-RateLimit-Remaining"));
        assertTrue(blockedRes.getContentAsString().contains("Rate limit exceeded"));
    }

    @Test
    void testDifferentIpsHaveIndependentLimits() throws ServletException, IOException {
        String ip1 = "10.0.0.1";
        String ip2 = "10.0.0.2";

        // Exhaust IP 1
        for (int i = 0; i < 3; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/chat");
            req.setRemoteAddr(ip1);
            MockHttpServletResponse res = new MockHttpServletResponse();
            rateLimiter.doFilter(req, res, filterChain);
            assertEquals(200, res.getStatus());
        }

        // IP 1 4th is blocked
        MockHttpServletRequest blockedReq = new MockHttpServletRequest("POST", "/chat");
        blockedReq.setRemoteAddr(ip1);
        MockHttpServletResponse blockedRes = new MockHttpServletResponse();
        rateLimiter.doFilter(blockedReq, blockedRes, filterChain);
        assertEquals(429, blockedRes.getStatus());

        // IP 2 is fresh and still allowed
        MockHttpServletRequest freshReq = new MockHttpServletRequest("POST", "/chat");
        freshReq.setRemoteAddr(ip2);
        MockHttpServletResponse freshRes = new MockHttpServletResponse();
        rateLimiter.doFilter(freshReq, freshRes, filterChain);
        assertEquals(200, freshRes.getStatus());
    }

    @Test
    void testTokensRefillOverTime() throws ServletException, IOException {
        String ip = "172.16.0.5";

        // Exhaust all 3 tokens
        for (int i = 0; i < 3; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/chat");
            req.setRemoteAddr(ip);
            MockHttpServletResponse res = new MockHttpServletResponse();
            rateLimiter.doFilter(req, res, filterChain);
            assertEquals(200, res.getStatus());
        }

        // Advance simulated time by 20 seconds (1 token refilled: 3 tokens / 60s = 1 token per 20s)
        fakeNanoTime.addAndGet(20L * 1_000_000_000L);

        // Next request should succeed
        MockHttpServletRequest reqAfterRefill = new MockHttpServletRequest("POST", "/chat");
        reqAfterRefill.setRemoteAddr(ip);
        MockHttpServletResponse resAfterRefill = new MockHttpServletResponse();

        rateLimiter.doFilter(reqAfterRefill, resAfterRefill, filterChain);
        assertEquals(200, resAfterRefill.getStatus());
    }

    @Test
    void testNonChatEndpointsBypassRateLimiter() throws ServletException, IOException {
        MockHttpServletRequest req1 = new MockHttpServletRequest("POST", "/deflection");
        MockHttpServletResponse res1 = new MockHttpServletResponse();
        rateLimiter.doFilter(req1, res1, filterChain);
        assertEquals(200, res1.getStatus());
        verify(filterChain).doFilter(req1, res1);

        MockHttpServletRequest req2 = new MockHttpServletRequest("GET", "/search");
        MockHttpServletResponse res2 = new MockHttpServletResponse();
        rateLimiter.doFilter(req2, res2, filterChain);
        assertEquals(200, res2.getStatus());
        verify(filterChain).doFilter(req2, res2);
    }

    @Test
    void testXForwardedForClientIpExtraction() throws ServletException, IOException {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/chat");
        req.addHeader("X-Forwarded-For", "203.0.113.195, 70.41.3.18, 150.172.238.178");
        req.setRemoteAddr("127.0.0.1");
        MockHttpServletResponse res = new MockHttpServletResponse();

        rateLimiter.doFilter(req, res, filterChain);

        assertEquals(200, res.getStatus());
        assertEquals("203.0.113.195", ChatRateLimiterFilter.extractClientIp(req));
    }

    @Test
    void testOptionsRequestSkipsRateLimitingAndDoesNotConsumeToken() throws ServletException, IOException {
        String ip = "192.168.1.50";

        // Exhaust all 3 tokens with POST requests
        for (int i = 0; i < 3; i++) {
            MockHttpServletRequest postReq = new MockHttpServletRequest("POST", "/chat");
            postReq.setRemoteAddr(ip);
            MockHttpServletResponse postRes = new MockHttpServletResponse();
            rateLimiter.doFilter(postReq, postRes, filterChain);
            assertEquals(200, postRes.getStatus());
        }

        // Token bucket is now empty. Next POST request will be 429.
        // However, a preflight OPTIONS request to /chat must NOT be blocked or 429'd.
        MockHttpServletRequest optionsReq = new MockHttpServletRequest("OPTIONS", "/chat");
        optionsReq.setRemoteAddr(ip);
        MockHttpServletResponse optionsRes = new MockHttpServletResponse();

        rateLimiter.doFilter(optionsReq, optionsRes, filterChain);

        assertEquals(200, optionsRes.getStatus());
        verify(filterChain).doFilter(optionsReq, optionsRes);
    }

    @Test
    void testDisabledRateLimiterAllowsAllRequests() throws ServletException, IOException {
        ChatRateLimiterFilter disabledLimiter = new ChatRateLimiterFilter(false, 1.0, 1.0, 60L, fakeNanoTime::get);

        for (int i = 0; i < 5; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/chat");
            req.setRemoteAddr("10.10.10.10");
            MockHttpServletResponse res = new MockHttpServletResponse();
            disabledLimiter.doFilter(req, res, filterChain);
            assertEquals(200, res.getStatus());
        }
    }
}
