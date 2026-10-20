package com.omsai.coaching.course;

import com.omsai.coaching.common.BusinessRuleException;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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
 * Course management pages.
 */
@Controller
@RequestMapping("/courses")
@PreAuthorize("hasRole('ADMIN')")
public class CourseController {

    private static final int PAGE_SIZE = 20;

    private final CourseService courseService;

    public CourseController(CourseService courseService) {
        this.courseService = courseService;
    }

    @GetMapping
    public String list(@RequestParam(required = false) String q,
                       @RequestParam(required = false) String status,
                       @RequestParam(defaultValue = "0") int page,
                       Model model) {
        Course.Status statusFilter = parseStatus(status);
        Pageable pageable = PageRequest.of(page, PAGE_SIZE, Sort.by("name"));
        model.addAttribute("courses", courseService.search(q, statusFilter, pageable));
        model.addAttribute("q", q);
        model.addAttribute("status", status);
        model.addAttribute("pagerUrl", pagerUrl(q, status));
        return "courses/list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("form", new CourseForm());
        return "courses/form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("form") CourseForm form,
                         BindingResult binding,
                         Model model,
                         RedirectAttributes redirect) {
        if (binding.hasErrors()) {
            return "courses/form";
        }
        try {
            Course created = courseService.create(form);
            redirect.addFlashAttribute("successMessage", "Course created.");
            return "redirect:/courses/" + created.getId();
        } catch (BusinessRuleException e) {
            bindError(model, e);
            return "courses/form";
        }
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        Course course = courseService.get(id);
        model.addAttribute("course", course);
        model.addAttribute("assignedSubjects", courseService.assignedSubjects(course));
        model.addAttribute("assignableSubjects", courseService.assignableSubjects(course));
        model.addAttribute("batches", courseService.batchesOf(id));
        return "courses/detail";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        Course course = courseService.get(id);
        model.addAttribute("course", course);
        model.addAttribute("form", fromCourse(course));
        return "courses/form";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id,
                         @Valid @ModelAttribute("form") CourseForm form,
                         BindingResult binding,
                         Model model,
                         RedirectAttributes redirect) {
        Course course = courseService.get(id);
        model.addAttribute("course", course);
        if (binding.hasErrors()) {
            return "courses/form";
        }
        try {
            courseService.update(id, form);
            redirect.addFlashAttribute("successMessage", "Course updated.");
            return "redirect:/courses/" + id;
        } catch (BusinessRuleException e) {
            bindError(model, e);
            return "courses/form";
        }
    }

    @PostMapping("/{id}/status")
    public String toggleStatus(@PathVariable Long id,
                               @RequestParam boolean active,
                               RedirectAttributes redirect) {
        Course course = courseService.setStatus(id, active);
        redirect.addFlashAttribute("successMessage",
                active ? "Course activated." : "Course deactivated. Existing batches and history are preserved.");
        return "redirect:/courses/" + course.getId();
    }

    @PostMapping("/{id}/subjects")
    public String assignSubject(@PathVariable Long id,
                                @RequestParam Long subjectId,
                                RedirectAttributes redirect) {
        try {
            courseService.assignSubject(id, subjectId);
            redirect.addFlashAttribute("successMessage", "Subject assigned to course.");
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/courses/" + id;
    }

    @PostMapping("/{id}/subjects/{courseSubjectId}/remove")
    public String removeSubject(@PathVariable Long id,
                                @PathVariable Long courseSubjectId,
                                RedirectAttributes redirect) {
        try {
            courseService.removeSubject(id, courseSubjectId);
            redirect.addFlashAttribute("successMessage", "Subject removed from course.");
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/courses/" + id;
    }

    private static CourseForm fromCourse(Course course) {
        CourseForm form = new CourseForm();
        form.setName(course.getName());
        form.setCategory(course.getCategory());
        form.setDescription(course.getDescription());
        form.setDurationMonths(course.getDurationMonths());
        return form;
    }

    private static Course.Status parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return Course.Status.valueOf(status);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String pagerUrl(String q, String status) {
        StringBuilder url = new StringBuilder("/courses");
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

    private void bindError(Model model, BusinessRuleException e) {
        model.addAttribute("errorMessage", e.getMessage());
    }
}
