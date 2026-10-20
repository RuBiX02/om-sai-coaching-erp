package com.omsai.coaching.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Rejects sign-in attempts for combinations of username + IP that have
 * exceeded the configured failure limit, before any password check runs.
 */
@Component
public class LoginRateLimitFilter extends OncePerRequestFilter {

    private static final PathPatternRequestMatcher LOGIN_POST =
            PathPatternRequestMatcher.pathPattern(HttpMethod.POST, "/login");

    private final LoginRateLimiter limiter;

    public LoginRateLimitFilter(LoginRateLimiter limiter) {
        this.limiter = limiter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !LOGIN_POST.matches(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String username = request.getParameter("username");
        String ip = clientIp(request);
        if (limiter.isBlocked(username, ip)) {
            response.sendRedirect("/login?locked");
            return;
        }
        chain.doFilter(request, response);
    }

    static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
