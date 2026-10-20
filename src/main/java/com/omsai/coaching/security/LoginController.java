package com.omsai.coaching.security;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Serves the login page. Actual authentication is handled by Spring
 * Security's form login processing filter.
 */
@Controller
public class LoginController {

    @GetMapping("/login")
    public String loginPage() {
        return "auth/login";
    }
}
