package com.omsai.coaching.subject;

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
 * Subject management pages.
 */
@Controller
@RequestMapping("/subjects")
@PreAuthorize("hasRole('ADMIN')")
public class SubjectController {

    private final SubjectService subjectService;

    public SubjectController(SubjectService subjectService) {
        this.subjectService = subjectService;
    }

    @GetMapping
    public String list(@RequestParam(required = false) String q,
                       @RequestParam(defaultValue = "0") int page,
                       Model model) {
        var pageable = PageRequest.of(page, 20, Sort.by("name"));
        model.addAttribute("subjects", subjectService.search(q, pageable));
        model.addAttribute("q", q);
        model.addAttribute("pagerUrl", (q == null || q.isBlank()) ? "/subjects" : "/subjects?q=" + q);
        return "subjects/list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("form", new SubjectForm());
        return "subjects/form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("form") SubjectForm form,
                         BindingResult binding,
                         Model model,
                         RedirectAttributes redirect) {
        if (binding.hasErrors()) {
            return "subjects/form";
        }
        try {
            Subject created = subjectService.create(form);
            redirect.addFlashAttribute("successMessage", "Subject created.");
            return "redirect:/subjects/" + created.getId();
        } catch (BusinessRuleException e) {
            model.addAttribute("errorMessage", e.getMessage());
            return "subjects/form";
        }
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        Subject subject = subjectService.get(id);
        model.addAttribute("subject", subject);
        model.addAttribute("courseSubjects", subjectService.coursesOf(subject));
        return "subjects/detail";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        Subject subject = subjectService.get(id);
        model.addAttribute("subject", subject);
        SubjectForm form = new SubjectForm();
        form.setName(subject.getName());
        form.setDescription(subject.getDescription());
        model.addAttribute("form", form);
        return "subjects/form";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id,
                         @Valid @ModelAttribute("form") SubjectForm form,
                         BindingResult binding,
                         Model model,
                         RedirectAttributes redirect) {
        Subject subject = subjectService.get(id);
        model.addAttribute("subject", subject);
        if (binding.hasErrors()) {
            return "subjects/form";
        }
        try {
            subjectService.update(id, form);
            redirect.addFlashAttribute("successMessage", "Subject updated.");
            return "redirect:/subjects/" + id;
        } catch (BusinessRuleException e) {
            model.addAttribute("errorMessage", e.getMessage());
            return "subjects/form";
        }
    }
}
