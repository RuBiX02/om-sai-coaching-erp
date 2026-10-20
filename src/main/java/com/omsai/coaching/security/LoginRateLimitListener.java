package com.omsai.coaching.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.authentication.event.LogoutSuccessEvent;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Feeds authentication outcomes into the login rate limiter. The client
 * IP is read from the current request when available.
 */
@Component
public class LoginRateLimitListener {

    private final LoginRateLimiter limiter;

    public LoginRateLimitListener(LoginRateLimiter limiter) {
        this.limiter = limiter;
    }

    @EventListener
    public void onFailure(AbstractAuthenticationFailureEvent event) {
        Authentication auth = event.getAuthentication();
        String username = auth == null || auth.getPrincipal() == null ? null : auth.getPrincipal().toString();
        limiter.recordFailure(username, currentIp());
    }

    @EventListener
    public void onSuccess(AuthenticationSuccessEvent event) {
        Authentication auth = event.getAuthentication();
        String username = auth == null || auth.getPrincipal() == null ? null : auth.getPrincipal().toString();
        limiter.clear(username, currentIp());
    }

    private String currentIp() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            HttpServletRequest request = attributes.getRequest();
            return LoginRateLimitFilter.clientIp(request);
        }
        return "";
    }
}
