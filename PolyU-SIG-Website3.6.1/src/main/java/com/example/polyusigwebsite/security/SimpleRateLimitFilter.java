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
    private final Map<String, Counter> counters = new ConcurrentHashMap<>();

    public SimpleRateLimitFilter(
            @Value("${app.rate-limit.requests-per-minute:120}") int requestsPerMinute,
            @Value("${app.rate-limit.login-per-minute:20}") int loginRequestsPerMinute
    ) {
        this.requestsPerMinute = requestsPerMinute;
        this.loginRequestsPerMinute = loginRequestsPerMinute;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        if (!path.startsWith("/api/")) {
            filterChain.doFilter(request, response);
            return;
        }

        int limit = path.startsWith("/api/auth/login") ? loginRequestsPerMinute : requestsPerMinute;
        String key = request.getRemoteAddr() + ":" + path;
        if (!allowRequest(key, limit)) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json");
            response.getWriter().write("{\"message\":\"Too many requests, please try again later.\"}");
            return;
        }

        filterChain.doFilter(request, response);
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
