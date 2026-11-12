package com.notetaking.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.notetaking.dto.response.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Order(1)
@RequiredArgsConstructor
public class LoginRateLimitFilter extends OncePerRequestFilter {

    private static final int MAX_ATTEMPTS = 5;
    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final Map<String, Deque<Instant>> attempts = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (!"/api/v1/auth/login".equals(request.getRequestURI())
                || !"POST".equals(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }

        // server.forward-headers-strategy=framework causes Spring's ForwardedHeaderFilter to
        // populate getRemoteAddr() from X-Forwarded-For. Reading the raw header here instead
        // would take the leftmost (client-controlled) value and allow IP spoofing bypass.
        String ip = request.getRemoteAddr();
        Deque<Instant> timestamps = attempts.computeIfAbsent(ip, k -> new ArrayDeque<>());

        synchronized (timestamps) {
            Instant cutoff = Instant.now().minus(WINDOW);
            while (!timestamps.isEmpty() && timestamps.peekFirst().isBefore(cutoff)) {
                timestamps.pollFirst();
            }
            if (timestamps.size() >= MAX_ATTEMPTS) {
                response.setStatus(429);
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                objectMapper.writeValue(response.getWriter(), ErrorResponse.builder()
                        .timestamp(LocalDateTime.now())
                        .status(429)
                        .error("Too Many Requests")
                        .message("Too many login attempts. Try again in 1 minute.")
                        .path(request.getRequestURI())
                        .build());
                return;
            }
        }

        chain.doFilter(request, response);

        // Only count failed authentication attempts. Counting successful logins would lock out
        // users who log in frequently (e.g. mobile apps that re-auth on each launch).
        if (response.getStatus() == HttpServletResponse.SC_UNAUTHORIZED) {
            synchronized (timestamps) {
                timestamps.addLast(Instant.now());
            }
        }
    }
}
