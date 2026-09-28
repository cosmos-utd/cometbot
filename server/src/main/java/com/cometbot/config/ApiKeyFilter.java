package com.cometbot.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Requires an {@code X-API-Key} header on /api/** when {@code app.api.key} is set.
 * With no key configured (local dev) the API is open and a warning is logged at startup.
 */
@Component
public class ApiKeyFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-API-Key";
    private static final Logger log = LoggerFactory.getLogger(ApiKeyFilter.class);

    private final byte[] apiKey;

    public ApiKeyFilter(@Value("${app.api.key:}") String apiKey) {
        this.apiKey = apiKey.getBytes(StandardCharsets.UTF_8);
        if (apiKey.isBlank()) {
            log.warn("app.api.key (API_KEY) is not set - the REST API under /api is unauthenticated");
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return apiKey.length == 0 || !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String given = request.getHeader(HEADER);
        if (given != null && MessageDigest.isEqual(apiKey, given.getBytes(StandardCharsets.UTF_8))) {
            chain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"error\":\"missing or invalid " + HEADER + " header\"}");
    }
}
