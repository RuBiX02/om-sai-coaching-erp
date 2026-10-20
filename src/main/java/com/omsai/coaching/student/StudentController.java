package com.omsai.coaching.student;

import com.omsai.coaching.batch.Batch;
import com.omsai.coaching.batch.BatchService;
import com.omsai.coaching.common.BusinessRuleException;
import com.omsai.coaching.enrollment.EnrollForm;
import com.omsai.coaching.enrollment.EnrollmentService;
import com.omsai.coaching.enrollment.TransferForm;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
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

import java.time.LocalDate;
import java.util.List;

/**
 * Student management pages, including enrollment and transfer actions.
 */
@Controller
@RequestMapping("/students")
@PreAuthorize("hasRole('ADMIN')")
public class StudentController {

    private final StudentService studentService;
    private final BatchService batchService;
    private final EnrollmentService enrollmentService;

    public StudentController(StudentService studentService,
                             BatchService batchService,
                             EnrollmentService enrollmentService) {
        this.studentService = studentService;
        this.batchService = batchService;
        this.enrollmentService = enrollmentService;
    }

    @GetMapping
    public String list(@RequestParam(required = false) String q,
                       @RequestParam(required = false) String status,
                       @RequestParam(defaultValue = "0") int page,
                       Model model) {
        var pageable = PageRequest.of(page, 20, Sort.by("fullName"));
        Boolean activeFilter = parseActive(status);
        model.addAttribute("students", studentService.search(q, activeFilter, pageable));
        model.addAttribute("q", q);
        model.addAttribute("status", status);
        model.addAttribute("pagerUrl", pagerUrl(q, status));
        return "students/list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        StudentForm form = new StudentForm();
        form.setAdmissionDate(LocalDate.now());
        model.addAttribute("form", form);
        return "students/form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("form") StudentForm form,
                         BindingResult binding,
                         Model model,
                         RedirectAttributes redirect) {
        if (binding.hasErrors()) {
            return "students/form";
        }
        try {
            Student created = studentService.create(form);
            redirect.addFlashAttribute("successMessage",
                    "Student registered with code " + created.getStudentCode() + ".");
            return "redirect:/students/" + created.getId();
        } catch (BusinessRuleException e) {
            model.addAttribute("errorMessage", e.getMessage());
            return "students/form";
        }
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        Student student = studentService.get(id);
        model.addAttribute("student", student);
        model.addAttribute("enrollments", studentService.enrollmentsOf(student));
        if (!model.containsAttribute("enrollForm")) {
            model.addAttribute("enrollForm", newEnrollForm(id));
        }
        if (!model.containsAttribute("transferForm")) {
            model.addAttribute("transferForm", newTransferForm(id));
        }
        model.addAttribute("activeBatches", batchService.activeBatches());
        return "students/detail";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        Student student = studentService.get(id);
        model.addAttribute("student", student);
        model.addAttribute("form", fromStudent(student));
        return "students/form";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id,
                         @Valid @ModelAttribute("form") StudentForm form,
                         BindingResult binding,
                         Model model,
                         RedirectAttributes redirect) {
        Student student = studentService.get(id);
        model.addAttribute("student", student);
        if (binding.hasErrors()) {
            return "students/form";
        }
        try {
            studentService.update(id, form);
            redirect.addFlashAttribute("successMessage", "Student details updated.");
            return "redirect:/students/" + id;
        } catch (BusinessRuleException e) {
            model.addAttribute("errorMessage", e.getMessage());
            return "students/form";
        }
    }

    @PostMapping("/{id}/status")
    public String toggleStatus(@PathVariable Long id,
                               @RequestParam boolean active,
                               RedirectAttributes redirect) {
        Student student = studentService.setStatus(id, active);
        redirect.addFlashAttribute("successMessage",
                active ? "Student activated." : "Student deactivated. Enrollments and history are preserved.");
        return "redirect:/students/" + student.getId();
    }

    @PostMapping("/{id}/enroll")
    public String enroll(@PathVariable Long id,
                         @Valid @ModelAttribute("enrollForm") EnrollForm form,
                         BindingResult binding,
                         RedirectAttributes redirect) {
        form.setStudentId(id);
        if (binding.hasErrors()) {
            redirect.addFlashAttribute("org.springframework.validation.BindingResult.enrollForm", binding);
            redirect.addFlashAttribute("enrollForm", form);
            return "redirect:/students/" + id;
        }
        try {
            enrollmentService.enroll(form);
            redirect.addFlashAttribute("successMessage", "Student enrolled.");
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/students/" + id;
    }

    @PostMapping("/{id}/transfer")
    public String transfer(@PathVariable Long id,
                           @Valid @ModelAttribute("transferForm") TransferForm form,
                           BindingResult binding,
                           RedirectAttributes redirect) {
        form.setStudentId(id);
        if (binding.hasErrors()) {
            redirect.addFlashAttribute("org.springframework.validation.BindingResult.transferForm", binding);
            redirect.addFlashAttribute("transferForm", form);
            return "redirect:/students/" + id;
        }
        try {
            enrollmentService.transfer(form);
            redirect.addFlashAttribute("successMessage",
                    "Student transferred. Previous enrollment was closed and the history is preserved.");
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/students/" + id;
    }

    /** Closes a single ACTIVE enrollment as COMPLETED or WITHDRAWN. */
    @PostMapping("/enrollments/{enrollmentId}/status")
    public String closeEnrollment(@PathVariable Long enrollmentId,
                                  @RequestParam Long studentId,
                                  @RequestParam String action,
                                  RedirectAttributes redirect) {
        try {
            if ("complete".equals(action)) {
                enrollmentService.complete(enrollmentId);
                redirect.addFlashAttribute("successMessage", "Enrollment marked as completed.");
            } else if ("withdraw".equals(action)) {
                enrollmentService.withdraw(enrollmentId);
                redirect.addFlashAttribute("successMessage", "Enrollment withdrawn.");
            } else {
                redirect.addFlashAttribute("errorMessage", "Unknown action.");
            }
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/students/" + studentId;
    }

    // ----- helpers ----------------------------------------------------------

    private static EnrollForm newEnrollForm(Long studentId) {
        EnrollForm form = new EnrollForm();
        form.setStudentId(studentId);
        form.setEnrollmentDate(LocalDate.now());
        return form;
    }

    private static TransferForm newTransferForm(Long studentId) {
        TransferForm form = new TransferForm();
        form.setStudentId(studentId);
        form.setTransferDate(LocalDate.now());
        return form;
    }

    private static StudentForm fromStudent(Student student) {
        StudentForm form = new StudentForm();
        form.setFullName(student.getFullName());
        form.setPhone(student.getPhone());
        form.setParentName(student.getParentName());
        form.setParentPhone(student.getParentPhone());
        form.setAddress(student.getAddress());
        form.setAdmissionDate(student.getAdmissionDate());
        return form;
    }

    private static String pagerUrl(String q, String status) {
        StringBuilder url = new StringBuilder("/students");
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

    private static Boolean parseActive(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        return "ACTIVE".equalsIgnoreCase(status) ? Boolean.TRUE
                : "INACTIVE".equalsIgnoreCase(status) ? Boolean.FALSE : null;
    }
}
