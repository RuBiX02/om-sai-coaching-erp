package com.omsai.coaching.common;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Renders the access-denied page when Spring Security forwards to
 * /error/403, including CSRF failures from POST requests. The forward
 * preserves the request method, so the mapping accepts any method.
 */
@Controller
public class ErrorViewController {

    @RequestMapping("/error/403")
    public String accessDenied() {
        return "error/403";
    }
}
