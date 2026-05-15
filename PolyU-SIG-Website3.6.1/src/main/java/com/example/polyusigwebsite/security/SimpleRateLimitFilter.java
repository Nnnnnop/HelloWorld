package com.example.polyusigwebsite.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SimpleRateLimitFilter extends OncePerRequestFilter {

    private final int requestsPerMinute;
    private final int loginRequestsPerMinute;
    private final int foldersPostPerMinute;
    private final int uploadSessionFilePostPerMinute;
    private final Map<String, Counter> counters = new ConcurrentHashMap<>();

    public SimpleRateLimitFilter(
            @Value("${app.rate-limit.requests-per-minute:120}") int requestsPerMinute,
            @Value("${app.rate-limit.login-per-minute:20}") int loginRequestsPerMinute,
            @Value("${app.rate-limit.folders-post-per-minute:8000}") int foldersPostPerMinute,
            @Value("${app.rate-limit.upload-session-file-post-per-minute:15000}") int uploadSessionFilePostPerMinute
    ) {
        this.requestsPerMinute = requestsPerMinute;
        this.loginRequestsPerMinute = loginRequestsPerMinute;
        this.foldersPostPerMinute = foldersPostPerMinute;
        this.uploadSessionFilePostPerMinute = uploadSessionFilePostPerMinute;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String servletPath = effectiveServletPath(request);
        if (!servletPath.startsWith("/api/")) {
            filterChain.doFilter(request, response);
            return;
        }

        String method = request.getMethod() != null ? request.getMethod().toUpperCase() : "";
        int limit = resolveLimit(servletPath, method);
        // Bucket by logical endpoint so one bulk folder upload does not consume the global /api/* budget
        String rateKeySuffix = rateKeySuffix(servletPath, method);
        String key = request.getRemoteAddr() + ":" + rateKeySuffix;
        if (!allowRequest(key, limit)) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json");
            response.getWriter().write("{\"message\":\"Too many requests, please try again later.\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Path without context path; matches Spring MVC mapping style used in controllers.
     */
    private static String effectiveServletPath(HttpServletRequest request) {
        String sp = request.getServletPath();
        if (sp != null && !sp.isEmpty()) {
            return sp;
        }
        String uri = request.getRequestURI();
        String context = request.getContextPath();
        if (context != null && !context.isEmpty() && uri != null && uri.startsWith(context)) {
            return uri.substring(context.length());
        }
        return uri != null ? uri : "";
    }

    private int resolveLimit(String servletPath, String method) {
        if (servletPath.startsWith("/api/auth/login")) {
            return loginRequestsPerMinute;
        }
        if ("POST".equals(method) && "/api/folders".equals(servletPath)) {
            return foldersPostPerMinute;
        }
        if ("POST".equals(method) && isUploadSessionFilePath(servletPath)) {
            return uploadSessionFilePostPerMinute;
        }
        return requestsPerMinute;
    }

    /**
     * One rate bucket for all session chunk uploads (same session shares one URL prefix pattern).
     */
    private static String rateKeySuffix(String servletPath, String method) {
        if ("POST".equals(method) && isUploadSessionFilePath(servletPath)) {
            return "POST:/api/files/upload/session/*/file";
        }
        return method + ":" + servletPath;
    }

    private static boolean isUploadSessionFilePath(String servletPath) {
        return servletPath.startsWith("/api/files/upload/session/")
                && servletPath.endsWith("/file");
    }

    private boolean allowRequest(String key, int limit) {
        long nowMinute = Instant.now().getEpochSecond() / 60;
        Counter counter = counters.compute(key, (k, old) -> {
            if (old == null || old.minute != nowMinute) {
                return new Counter(nowMinute, 1);
            }
            old.count++;
            return old;
        });
        return counter.count <= limit;
    }

    private static final class Counter {
        private final long minute;
        private int count;

        private Counter(long minute, int count) {
            this.minute = minute;
            this.count = count;
        }
    }
}
