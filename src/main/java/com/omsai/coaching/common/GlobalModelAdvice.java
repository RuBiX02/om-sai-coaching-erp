package com.omsai.coaching.security;

import com.omsai.coaching.security.AppUserPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Adds common model attributes available to every view, most notably
 * the signed-in user's display name for the top bar.
 */
@ControllerAdvice
public class GlobalModelAdvice {

    @ModelAttribute
    public void addCommonAttributes(Model model) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AppUserPrincipal principal) {
            model.addAttribute("userName", principal.fullName());
        }
    }
}
