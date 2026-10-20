package com.omsai.coaching.batch;

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

import java.util.List;

/**
 * Batch management pages, including subject and teacher assignment.
 */
@Controller
@RequestMapping("/batches")
@PreAuthorize("hasRole('ADMIN')")
public class BatchController {

    private final BatchService batchService;

    public BatchController(BatchService batchService) {
        this.batchService = batchService;
    }

    @GetMapping
    public String list(@RequestParam(required = false) String q,
                       @RequestParam(required = false) Long courseId,
                       @RequestParam(required = false) String status,
                       @RequestParam(defaultValue = "0") int page,
                       Model model) {
        Batch.Status statusFilter = parseStatus(status);
        var pageable = PageRequest.of(page, 20, Sort.by(Sort.Direction.DESC, "academicYear").and(Sort.by("name")));
        model.addAttribute("batches", batchService.search(q, courseId, statusFilter, pageable));
        model.addAttribute("courses", batchService.activeCourses());
        model.addAttribute("allStatuses", List.of(Batch.Status.values()));
        model.addAttribute("q", q);
        model.addAttribute("courseId", courseId);
        model.addAttribute("status", status);
        model.addAttribute("pagerUrl", pagerUrl(q, courseId, status));
        return "batches/list";
    }

    @GetMapping("/new")
    public String newForm(@RequestParam(required = false) Long courseId, Model model) {
        BatchForm form = new BatchForm();
        if (courseId != null) {
            form.setCourseId(courseId);
        }
        form.setAcademicYear(defaultAcademicYear());
        model.addAttribute("form", form);
        model.addAttribute("courses", batchService.activeCourses());
        return "batches/form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("form") BatchForm form,
                         BindingResult binding,
                         Model model,
                         RedirectAttributes redirect) {
        if (binding.hasErrors()) {
            model.addAttribute("courses", batchService.activeCourses());
            return "batches/form";
        }
        try {
            Batch created = batchService.create(form);
            redirect.addFlashAttribute("successMessage", "Batch created.");
            return "redirect:/batches/" + created.getId();
        } catch (BusinessRuleException e) {
            model.addAttribute("courses", batchService.activeCourses());
            model.addAttribute("errorMessage", e.getMessage());
            return "batches/form";
        }
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        Batch batch = batchService.get(id);
        model.addAttribute("batch", batch);
        model.addAttribute("batchSubjects", batchService.subjectsOf(batch));
        model.addAttribute("assignableSubjects", batchService.assignableCourseSubjects(batch));
        model.addAttribute("teachers", batchService.activeTeachers());
        model.addAttribute("enrollments", batchService.enrollmentsOf(batch));
        return "batches/detail";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        Batch batch = batchService.get(id);
        model.addAttribute("batch", batch);
        model.addAttribute("form", fromBatch(batch));
        model.addAttribute("courses", batchService.activeCourses());
        return "batches/form";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id,
                         @Valid @ModelAttribute("form") BatchForm form,
                         BindingResult binding,
                         Model model,
                         RedirectAttributes redirect) {
        Batch batch = batchService.get(id);
        model.addAttribute("batch", batch);
        form.setCourseId(batch.getCourse().getId());
        if (binding.hasErrors()) {
            model.addAttribute("courses", batchService.activeCourses());
            return "batches/form";
        }
        try {
            batchService.update(id, form);
            redirect.addFlashAttribute("successMessage", "Batch updated.");
            return "redirect:/batches/" + id;
        } catch (BusinessRuleException e) {
            model.addAttribute("courses", batchService.activeCourses());
            model.addAttribute("errorMessage", e.getMessage());
            return "batches/form";
        }
    }

    @PostMapping("/{id}/status")
    public String toggleStatus(@PathVariable Long id,
                               @RequestParam boolean active,
                               RedirectAttributes redirect) {
        Batch batch = batchService.setStatus(id, active);
        redirect.addFlashAttribute("successMessage",
                active ? "Batch activated." : "Batch deactivated. Enrollments and history are preserved.");
        return "redirect:/batches/" + batch.getId();
    }

    @PostMapping("/{id}/subjects")
    public String addSubject(@PathVariable Long id,
                             @RequestParam Long courseSubjectId,
                             RedirectAttributes redirect) {
        try {
            batchService.addSubject(id, courseSubjectId);
            redirect.addFlashAttribute("successMessage", "Subject added to batch.");
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/batches/" + id;
    }

    @PostMapping("/subjects/{batchSubjectId}/remove")
    public String removeSubject(@PathVariable Long batchSubjectId,
                                @RequestParam Long batchId,
                                RedirectAttributes redirect) {
        try {
            batchService.removeSubject(batchSubjectId);
            redirect.addFlashAttribute("successMessage", "Subject removed from batch.");
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/batches/" + batchId;
    }

    @PostMapping("/subjects/{batchSubjectId}/teacher")
    public String assignTeacher(@PathVariable Long batchSubjectId,
                                @RequestParam Long batchId,
                                @RequestParam(required = false) Long teacherId,
                                RedirectAttributes redirect) {
        try {
            batchService.assignTeacher(batchSubjectId, teacherId);
            redirect.addFlashAttribute("successMessage",
                    teacherId == null ? "Teacher assignment cleared." : "Teacher assigned.");
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/batches/" + batchId;
    }

    // ----- helpers ----------------------------------------------------------

    private static BatchForm fromBatch(Batch batch) {
        BatchForm form = new BatchForm();
        form.setCourseId(batch.getCourse().getId());
        form.setName(batch.getName());
        form.setAcademicYear(batch.getAcademicYear());
        form.setStartDate(batch.getStartDate());
        form.setEndDate(batch.getEndDate());
        return form;
    }

    private static Batch.Status parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return Batch.Status.valueOf(status);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String defaultAcademicYear() {
        int year = java.time.LocalDate.now().getYear();
        return year + "-" + String.format("%02d", (year + 1) % 100);
    }

    private static String pagerUrl(String q, Long courseId, String status) {
        StringBuilder url = new StringBuilder("/batches");
        boolean first = true;
        if (q != null && !q.isBlank()) {
            url.append("?q=").append(q);
            first = false;
        }
        if (courseId != null) {
            url.append(first ? "?" : "&").append("courseId=").append(courseId);
            first = false;
        }
        if (status != null && !status.isBlank()) {
            url.append(first ? "?" : "&").append("status=").append(status);
        }
        return url.toString();
    }
}
