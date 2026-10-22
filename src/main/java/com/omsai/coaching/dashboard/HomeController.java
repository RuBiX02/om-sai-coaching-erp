package com.omsai.coaching.dashboard;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Root URL: sends every role to its own home page - administrators to
 * the management dashboard, teachers to their assignment view and
 * students to their portal.
 */
@Controller
public class HomeController {

    private final DashboardService dashboardService;

    public HomeController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/")
    public String home(Authentication authentication) {
        if (authentication == null) {
            return "redirect:/login";
        }
        if (hasRole(authentication, "ROLE_ADMIN")) {
            return "redirect:/dashboard";
        }
        if (hasRole(authentication, "ROLE_TEACHER")) {
            return "redirect:/teacher";
        }
        if (hasRole(authentication, "ROLE_STUDENT")) {
            return "redirect:/student";
        }
        return "redirect:/login";
    }

    private static boolean hasRole(Authentication authentication, String role) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(role::equals);
    }
}
