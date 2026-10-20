package com.omsai.coaching.security;

import com.omsai.coaching.common.BusinessRuleException;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Minimal account administration: list, create, deactivate/activate and
 * password reset. Administrator-only.
 */
@Controller
@RequestMapping("/users")
@PreAuthorize("hasRole('ADMIN')")
public class UserAdminController {

    private final UserService userService;

    public UserAdminController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public String list(@RequestParam(required = false) String q,
                       @RequestParam(defaultValue = "0") int page,
                       Model model) {
        var pageable = PageRequest.of(page, 20, Sort.by("fullName"));
        model.addAttribute("users", userService.search(q, pageable));
        model.addAttribute("q", q);
        model.addAttribute("pagerUrl", buildPagerUrl("/users", q));
        return "users/list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("form", new UserForm());
        return "users/form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("form") UserForm form,
                         BindingResult binding,
                         Model model,
                         RedirectAttributes redirect) {
        if (binding.hasErrors()) {
            return "users/form";
        }
        try {
            UserAccount created = userService.create(form);
            redirect.addFlashAttribute("successMessage", "Account created for " + created.getEmail() + ".");
            return "redirect:/users";
        } catch (BusinessRuleException e) {
            model.addAttribute("errorMessage", e.getMessage());
            return "users/form";
        }
    }

    @PostMapping("/{id}/status")
    public String toggleStatus(@PathVariable Long id,
                               @RequestParam boolean active,
                               RedirectAttributes redirect) {
        try {
            userService.setActive(id, active);
            redirect.addFlashAttribute("successMessage", active ? "Account activated." : "Account deactivated.");
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/users";
    }

    @PostMapping("/{id}/password")
    public String resetPassword(@PathVariable Long id,
                                @RequestParam String password,
                                RedirectAttributes redirect) {
        try {
            userService.resetPassword(id, password);
            redirect.addFlashAttribute("successMessage", "Password has been reset.");
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/users";
    }

    private static String buildPagerUrl(String path, String q) {
        return (q == null || q.isBlank()) ? path : path + "?q=" + q;
    }
}
