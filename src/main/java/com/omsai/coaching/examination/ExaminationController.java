package com.omsai.coaching.examination;

import com.omsai.coaching.batch.BatchRepository;
import com.omsai.coaching.batch.BatchSubjectRepository;
import com.omsai.coaching.common.BusinessClock;
import com.omsai.coaching.common.BusinessRuleException;
import com.omsai.coaching.enrollment.Enrollment;
import com.omsai.coaching.enrollment.EnrollmentRepository;
import com.omsai.coaching.security.AppUserPrincipal;
import com.omsai.coaching.security.UserAccount;
import com.omsai.coaching.security.UserAccountRepository;
import com.omsai.coaching.student.Student;
import com.omsai.coaching.student.StudentRepository;
import com.omsai.coaching.teacher.Teacher;
import com.omsai.coaching.teacher.TeacherRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Examination screens: the examinations, the bulk marks entry and the
 * academic reports.
 *
 * <p>Administrators manage every examination; a teacher only the
 * examinations of the classes they are assigned to, and never
 * publishes a result.</p>
 */
@Controller
@RequestMapping("/examinations")
public class ExaminationController {

    private final ExaminationService examinationService;
    private final BatchSubjectRepository batchSubjectRepository;
    private final BatchRepository batchRepository;
    private final TeacherRepository teacherRepository;
    private final StudentRepository studentRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final UserAccountRepository userAccountRepository;
    private final BusinessClock businessClock;

    public ExaminationController(ExaminationService examinationService,
                                 BatchSubjectRepository batchSubjectRepository,
                                 BatchRepository batchRepository,
                                 TeacherRepository teacherRepository,
                                 StudentRepository studentRepository,
                                 EnrollmentRepository enrollmentRepository,
                                 UserAccountRepository userAccountRepository,
                                 BusinessClock businessClock) {
        this.examinationService = examinationService;
        this.batchSubjectRepository = batchSubjectRepository;
        this.batchRepository = batchRepository;
        this.teacherRepository = teacherRepository;
        this.studentRepository = studentRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.userAccountRepository = userAccountRepository;
        this.businessClock = businessClock;
    }

    /** The examinations of the institute (or of the signed-in teacher). */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public String list(Model model) {
        UserAccount user = currentUser();
        boolean teacher = user.getRole() == UserAccount.Role.TEACHER;
        List<Examination> examinations;
        if (teacher) {
            Teacher profile = teacherRepository.findByLinkedUserId(user.getId()).orElse(null);
            examinations = profile == null ? List.of() : examinationService.forTeacher(profile.getId());
        } else {
            examinations = examinationService.all();
        }
        model.addAttribute("examinations", examinations);
        model.addAttribute("isTeacher", teacher);
        return "examination/list";
    }

    @GetMapping("/new")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public String newForm(Model model) {
        model.addAttribute("form", new ExaminationForm());
        model.addAttribute("assignments", assignableClasses());
        return "examination/form";
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public String create(@Valid @ModelAttribute("form") ExaminationForm form,
                         BindingResult binding,
                         Model model,
                         RedirectAttributes redirect) {
        if (binding.hasErrors()) {
            model.addAttribute("assignments", assignableClasses());
            return "examination/form";
        }
        try {
            Examination examination = examinationService.create(form, currentUser());
            redirect.addFlashAttribute("successMessage", "Examination created as a draft.");
            return "redirect:/examinations/" + examination.getId();
        } catch (AccessDeniedException e) {
            throw e;
        } catch (BusinessRuleException e) {
            model.addAttribute("assignments", assignableClasses());
            model.addAttribute("errorMessage", e.getMessage());
            return "examination/form";
        }
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public String detail(@PathVariable Long id, Model model) {
        UserAccount user = currentUser();
        Examination examination = examinationService.get(id);
        if (!isAdmin(user)) {
            Teacher profile = teacherRepository.findByLinkedUserId(user.getId()).orElse(null);
            boolean own = profile != null && examination.getBatchSubject().getTeacher() != null
                    && examination.getBatchSubject().getTeacher().getId().equals(profile.getId());
            if (!own) {
                throw new AccessDeniedException("You do not have access to this examination.");
            }
        }
        model.addAttribute("examination", examination);
        model.addAttribute("rows", examinationService.resultRows(examination));
        model.addAttribute("isAdmin", isAdmin(user));
        return "examination/detail";
    }

    @GetMapping("/{id}/edit")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public String editForm(@PathVariable Long id, Model model) {
        Examination examination = examinationService.get(id);
        model.addAttribute("examination", examination);
        model.addAttribute("form", fromExamination(examination));
        model.addAttribute("assignments", assignableClasses());
        return "examination/form";
    }

    @PostMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public String update(@PathVariable Long id,
                         @Valid @ModelAttribute("form") ExaminationForm form,
                         BindingResult binding,
                         Model model,
                         RedirectAttributes redirect) {
        if (binding.hasErrors()) {
            model.addAttribute("examination", examinationService.get(id));
            model.addAttribute("assignments", assignableClasses());
            return "examination/form";
        }
        try {
            examinationService.update(id, form, currentUser());
            redirect.addFlashAttribute("successMessage", "Examination updated.");
            return "redirect:/examinations/" + id;
        } catch (AccessDeniedException e) {
            throw e;
        } catch (BusinessRuleException e) {
            model.addAttribute("examination", examinationService.get(id));
            model.addAttribute("assignments", assignableClasses());
            model.addAttribute("errorMessage", e.getMessage());
            return "examination/form";
        }
    }

    @PostMapping("/{id}/publish")
    @PreAuthorize("hasRole('ADMIN')")
    public String publish(@PathVariable Long id, RedirectAttributes redirect) {
        try {
            examinationService.publish(id, currentUser());
            redirect.addFlashAttribute("successMessage",
                    "Examination published. The students can now see their results.");
        } catch (AccessDeniedException e) {
            throw e;
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/examinations/" + id;
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public String cancel(@PathVariable Long id, RedirectAttributes redirect) {
        try {
            examinationService.cancel(id, currentUser());
            redirect.addFlashAttribute("successMessage",
                    "Examination cancelled. Its recorded results are kept.");
        } catch (AccessDeniedException e) {
            throw e;
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/examinations/" + id;
    }

    // ----- marks -----------------------------------------------------------

    @GetMapping("/{id}/marks")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public String marksForm(@PathVariable Long id, Model model) {
        Examination examination = examinationService.get(id);
        model.addAttribute("examination", examination);
        model.addAttribute("rows", examinationService.resultRows(examination));
        model.addAttribute("statuses", ExaminationResult.Status.values());
        return "examination/marks";
    }

    @PostMapping("/{id}/marks")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public String saveMarks(@PathVariable Long id,
                             HttpServletRequest request,
                             Model model,
                             RedirectAttributes redirect) {
        List<ExaminationService.ResultInput> inputs = readResults(request);
        try {
            int saved = examinationService.recordResults(id, inputs, currentUser());
            redirect.addFlashAttribute("successMessage", "Saved " + saved + " result(s).");
            return "redirect:/examinations/" + id + "/marks";
        } catch (AccessDeniedException e) {
            throw e;
        } catch (BusinessRuleException e) {
            Examination examination = examinationService.get(id);
            model.addAttribute("examination", examination);
            model.addAttribute("rows", examinationService.resultRows(examination));
            model.addAttribute("statuses", ExaminationResult.Status.values());
            model.addAttribute("errorMessage", e.getMessage());
            return "examination/marks";
        }
    }

    // ----- reports ---------------------------------------------------------

    /** The academic history of one student. */
    @GetMapping("/reports/student/{studentId}")
    @PreAuthorize("hasRole('ADMIN')")
    public String studentReport(@PathVariable Long studentId, Model model) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new BusinessRuleException("Student not found."));
        List<Enrollment> enrollments = enrollmentRepository.findForStudent(studentId);
        List<ExaminationResult> results = new ArrayList<>();
        for (Enrollment enrollment : enrollments) {
            results.addAll(examinationService.resultsForEnrollment(enrollment.getId()));
        }
        model.addAttribute("student", student);
        model.addAttribute("enrollments", enrollments);
        model.addAttribute("results", results);
        return "examination/student-report";
    }

    /** The results of the examinations of one batch. */
    @GetMapping("/reports/batch")
    @PreAuthorize("hasRole('ADMIN')")
    public String batchReport(@RequestParam(required = false) Long batchId,
                              @RequestParam(required = false) LocalDate from,
                              @RequestParam(required = false) LocalDate to,
                              Model model) {
        model.addAttribute("batches", batchRepository.findAll());
        model.addAttribute("batchId", batchId);
        model.addAttribute("from", from);
        model.addAttribute("to", to);
        if (batchId != null) {
            List<Examination> examinations = examinationService.forBatch(batchId, from, to);
            model.addAttribute("summaries", examinationService.summariesFor(examinations));
        }
        return "examination/batch-report";
    }

    // ----- helpers ----------------------------------------------------------

    /** The classes the signed-in user may create an examination for. */
    private List<com.omsai.coaching.batch.BatchSubject> assignableClasses() {
        UserAccount user = currentUser();
        if (isAdmin(user)) {
            return batchSubjectRepository.findAllWithContext();
        }
        Teacher profile = teacherRepository.findByLinkedUserId(user.getId()).orElse(null);
        if (profile == null) {
            return List.of();
        }
        return batchSubjectRepository.findForTeacher(profile.getId());
    }

    /** The result rows of the marks form, read from the raw parameters. */
    private static List<ExaminationService.ResultInput> readResults(HttpServletRequest request) {
        Map<String, String[]> params = request.getParameterMap();
        List<ExaminationService.ResultInput> inputs = new ArrayList<>();
        for (String key : params.keySet()) {
            if (!key.startsWith("status_")) {
                continue;
            }
            String enrollmentId = key.substring("status_".length());
            String[] statusValues = params.get(key);
            String[] marksValues = params.get("marks_" + enrollmentId);
            String[] remarksValues = params.get("remarks_" + enrollmentId);
            inputs.add(new ExaminationService.ResultInput(
                    parseId(enrollmentId),
                    statusValues == null || statusValues.length == 0 ? null : statusValues[0],
                    marksValues == null || marksValues.length == 0 ? null : marksValues[0],
                    remarksValues == null || remarksValues.length == 0 ? null : remarksValues[0]));
        }
        return inputs;
    }

    private static Long parseId(String value) {
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private UserAccount currentUser() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication != null
                && authentication.getPrincipal() instanceof AppUserPrincipal principal)) {
            throw new AccessDeniedException("Not authenticated.");
        }
        return userAccountRepository.findById(principal.id())
                .orElseThrow(() -> new AccessDeniedException("Your account no longer exists."));
    }

    private static boolean isAdmin(UserAccount user) {
        return user.getRole() == UserAccount.Role.ADMIN;
    }

    private static ExaminationForm fromExamination(Examination examination) {
        ExaminationForm form = new ExaminationForm();
        form.setBatchSubjectId(examination.getBatchSubject().getId());
        form.setTitle(examination.getTitle());
        form.setDescription(examination.getDescription());
        form.setExaminationDate(examination.getExaminationDate());
        form.setMaximumMarks(examination.getMaximumMarks());
        return form;
    }
}
