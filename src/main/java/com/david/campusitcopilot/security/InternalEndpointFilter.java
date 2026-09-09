package com.david.campusitcopilot.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * Guards the operator-only endpoints ({@code /ingest} and {@code /search}) behind a shared
 * secret so they aren't publicly reachable once deployed. The student-facing {@code /chat}
 * endpoint is intentionally left open.
 * <p>
 * Protection activates only when {@code app.internal.api-key} is set — in local dev it can be
 * left blank so nothing gets in the way. When set, callers must send a matching
 * {@code X-Internal-Api-Key} header or they get a 401.
 */
@Component
public class InternalEndpointFilter extends OncePerRequestFilter {

    private static final String HEADER = "X-Internal-Api-Key";
    private static final Set<String> PROTECTED_PREFIXES = Set.of("/ingest", "/search");

    private final String apiKey;

    public InternalEndpointFilter(@Value("${app.internal.api-key:}") String apiKey) {
        this.apiKey = apiKey;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        if (StringUtils.hasText(apiKey) && isProtected(request) && !apiKey.equals(request.getHeader(HEADER))) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Internal endpoint");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean isProtected(HttpServletRequest request) {
        String path = request.getRequestURI();
        return PROTECTED_PREFIXES.stream().anyMatch(path::startsWith);
    }
}
