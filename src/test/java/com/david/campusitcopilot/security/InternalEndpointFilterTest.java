package com.david.campusitcopilot.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class InternalEndpointFilterTest {

    private static final String SECRET_KEY = "test-internal-secret-key-123";

    private InternalEndpointFilter filter;
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        filter = new InternalEndpointFilter(SECRET_KEY);
        filterChain = mock(FilterChain.class);
    }

    @Test
    void testIngestWithoutApiKeyReturns401() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/ingest");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.getStatus());
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    void testSearchWithInvalidApiKeyReturns401() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/search");
        request.addHeader("X-Internal-Api-Key", "wrong-key");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.getStatus());
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    void testIngestWithValidApiKeyProceeds() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/ingest");
        request.addHeader("X-Internal-Api-Key", SECRET_KEY);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertEquals(HttpServletResponse.SC_OK, response.getStatus());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void testSearchWithValidApiKeyProceeds() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/search");
        request.addHeader("X-Internal-Api-Key", SECRET_KEY);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertEquals(HttpServletResponse.SC_OK, response.getStatus());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void testChatEndpointIsOpenWithoutApiKey() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/chat");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertEquals(HttpServletResponse.SC_OK, response.getStatus());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void testDeflectionEndpointIsOpenWithoutApiKey() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/deflection");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertEquals(HttpServletResponse.SC_OK, response.getStatus());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void testBlankApiKeyDisablesProtection() throws ServletException, IOException {
        InternalEndpointFilter openFilter = new InternalEndpointFilter("");
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/ingest");
        MockHttpServletResponse response = new MockHttpServletResponse();

        openFilter.doFilter(request, response, filterChain);

        assertEquals(HttpServletResponse.SC_OK, response.getStatus());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void testOptionsRequestPassesWithoutApiKey() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/ingest");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertEquals(HttpServletResponse.SC_OK, response.getStatus());
        verify(filterChain).doFilter(request, response);
    }
}
