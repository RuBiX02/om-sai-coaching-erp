package com.omsai.coaching.teacher;

import com.omsai.coaching.common.BusinessRuleException;
import com.omsai.coaching.security.UserAccountRepository;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
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

import java.util.List;

/**
 * Teacher management pages, including optional account linking.
 */
@Controller
@RequestMapping("/teachers")
@PreAuthorize("hasRole('ADMIN')")
public class TeacherController {

    private final TeacherService teacherService;
    private final UserAccountRepository userRepository;
    private final com.omsai.coaching.batch.BatchService batchService;

    public TeacherController(TeacherService teacherService,
                             UserAccountRepository userRepository,
                             com.omsai.coaching.batch.BatchService batchService) {
        this.teacherService = teacherService;
        this.userRepository = userRepository;
        this.batchService = batchService;
    }

    @GetMapping
    public String list(@RequestParam(required = false) String q,
                       @RequestParam(required = false) String status,
                       @RequestParam(defaultValue = "0") int page,
                       Model model) {
        Boolean activeFilter = parseActive(status);
        var pageable = PageRequest.of(page, 20, Sort.by("fullName"));
        Page<Teacher> teachers = teacherService.search(q, activeFilter, pageable);
        model.addAttribute("teachers", teachers);
        model.addAttribute("q", q);
        model.addAttribute("status", status);
        model.addAttribute("pagerUrl", pagerUrl(q, status));
        return "teachers/list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("form", new TeacherForm());
        model.addAttribute("linkableAccounts", teacherService.linkableAccounts());
        return "teachers/form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("form") TeacherForm form,
                         BindingResult binding,
                         Model model,
                         RedirectAttributes redirect) {
        if (binding.hasErrors()) {
            model.addAttribute("linkableAccounts", teacherService.linkableAccounts());
            return "teachers/form";
        }
        try {
            Teacher created = teacherService.create(form);
            redirect.addFlashAttribute("successMessage", "Teacher profile created.");
            return "redirect:/teachers/" + created.getId();
        } catch (BusinessRuleException e) {
            model.addAttribute("linkableAccounts", teacherService.linkableAccounts());
            model.addAttribute("errorMessage", e.getMessage());
            return "teachers/form";
        }
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        Teacher teacher = teacherService.get(id);
        model.addAttribute("teacher", teacher);
        model.addAttribute("assignments", teacherService.assignmentsOf(teacher));
        return "teachers/detail";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        Teacher teacher = teacherService.get(id);
        model.addAttribute("teacher", teacher);
        model.addAttribute("form", fromTeacher(teacher));
        model.addAttribute("linkableAccounts", teacherService.linkableAccounts());
        return "teachers/form";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id,
                         @Valid @ModelAttribute("form") TeacherForm form,
                         BindingResult binding,
                         Model model,
                         RedirectAttributes redirect) {
        Teacher teacher = teacherService.get(id);
        model.addAttribute("teacher", teacher);
        if (binding.hasErrors()) {
            model.addAttribute("linkableAccounts", teacherService.linkableAccounts());
            return "teachers/form";
        }
        try {
            teacherService.update(id, form);
            redirect.addFlashAttribute("successMessage", "Teacher profile updated.");
            return "redirect:/teachers/" + id;
        } catch (BusinessRuleException e) {
            model.addAttribute("linkableAccounts", teacherService.linkableAccounts());
            model.addAttribute("errorMessage", e.getMessage());
            return "teachers/form";
        }
    }

    @PostMapping("/{id}/status")
    public String toggleStatus(@PathVariable Long id,
                               @RequestParam boolean active,
                               RedirectAttributes redirect) {
        Teacher teacher = teacherService.setStatus(id, active);
        redirect.addFlashAttribute("successMessage",
                active ? "Teacher activated." : "Teacher deactivated. Existing assignments and history are preserved.");
        return "redirect:/teachers/" + teacher.getId();
    }

    // ----- helpers ----------------------------------------------------------

    private static TeacherForm fromTeacher(Teacher teacher) {
        TeacherForm form = new TeacherForm();
        form.setFullName(teacher.getFullName());
        form.setPhone(teacher.getPhone());
        form.setEmail(teacher.getEmail());
        form.setUserId(teacher.getLinkedUser() == null ? null : teacher.getLinkedUser().getId());
        return form;
    }

    private static Boolean parseActive(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        return "ACTIVE".equalsIgnoreCase(status) ? Boolean.TRUE
                : "INACTIVE".equalsIgnoreCase(status) ? Boolean.FALSE : null;
    }

    private static String pagerUrl(String q, String status) {
        StringBuilder url = new StringBuilder("/teachers");
        boolean first = true;
        if (q != null && !q.isBlank()) {
            url.append("?q=").append(q);
            first = false;
        }
        if (status != null && !status.isBlank()) {
            url.append(first ? "?" : "&").append("status=").append(status);
        }
        return url.toString();
    }
}
