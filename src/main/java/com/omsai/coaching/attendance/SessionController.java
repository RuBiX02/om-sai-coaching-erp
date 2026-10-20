package com.omsai.coaching.attendance;

import com.omsai.coaching.batch.BatchRepository;
import com.omsai.coaching.common.BusinessClock;
import com.omsai.coaching.common.BusinessRuleException;
import com.omsai.coaching.security.UserAccount;
import com.omsai.coaching.teacher.Teacher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Class-session pages for administrators and (only for their own
 * assignments) teachers. All session and attendance authorization
 * rules live in the services; the controller only delegates. The
 * signed-in user is resolved from the security context and is never
 * bound from request parameters.
 */
@Controller
@RequestMapping("/sessions")
@PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
public class SessionController {

    private final ClassSessionService sessionService;
    private final AttendanceService attendanceService;
    private final AttendanceRepository attendanceRepository;
    private final SessionAuthority authority;
    private final BusinessClock businessClock;
    private final BatchRepository batchRepository;

    public SessionController(ClassSessionService sessionService,
                             AttendanceService attendanceService,
                             AttendanceRepository attendanceRepository,
                             SessionAuthority authority,
                             BusinessClock businessClock,
                             BatchRepository batchRepository) {
        this.sessionService = sessionService;
        this.attendanceService = attendanceService;
        this.attendanceRepository = attendanceRepository;
        this.authority = authority;
        this.businessClock = businessClock;
        this.batchRepository = batchRepository;
    }

    @GetMapping
    public String list(@RequestParam(required = false) Long batchId,
                       @RequestParam(required = false) Long subjectId,
                       @RequestParam(required = false) LocalDate from,
                       @RequestParam(required = false) LocalDate to,
                       @RequestParam(required = false) String status,
                       @RequestParam(defaultValue = "0") int page,
                       Model model) {
        UserAccount user = authority.currentUser();
        ClassSession.Status statusFilter = parseStatus(status);
        boolean isTeacher = authority.isTeacher(user);
        var pageable = PageRequest.of(page, 20, Sort.by(Sort.Direction.DESC, "sessionDate").and(Sort.by("id")));
        model.addAttribute("sessions",
                isTeacher
                        ? sessionService.searchForTeacher(teacherIdOf(user), from, to, statusFilter, pageable)
                        : sessionService.search(batchId, subjectId, from, to, statusFilter, pageable));
        model.addAttribute("statusFilter", status);
        model.addAttribute("batchId", batchId);
        model.addAttribute("subjectId", subjectId);
        model.addAttribute("from", from);
        model.addAttribute("to", to);
        model.addAttribute("isTeacher", isTeacher);
        if (!isTeacher) {
            model.addAttribute("batches",
                    batchRepository.findAll(Sort.by(Sort.Direction.DESC, "academicYear").and(Sort.by("name"))));
        }
        model.addAttribute("pagerUrl", pagerUrl(batchId, subjectId, from, to, status));
        return "sessions/list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        UserAccount user = authority.currentUser();
        SessionForm form = new SessionForm();
        form.setSessionDate(businessClock.today());
        model.addAttribute("form", form);
        model.addAttribute("cs", null);
        model.addAttribute("assignments", sessionService.assignableList(user));
        model.addAttribute("isTeacher", authority.isTeacher(user));
        return "sessions/form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("form") SessionForm form,
                         BindingResult binding,
                         Model model,
                         RedirectAttributes redirect) {
        UserAccount user = authority.currentUser();
        if (binding.hasErrors()) {
            commonFormModel(model, null, sessionService.assignableList(user), user);
            return "sessions/form";
        }
        try {
            ClassSession created = sessionService.create(form, user);
            redirect.addFlashAttribute("successMessage", "Class session created.");
            return "redirect:/sessions/" + created.getId();
        } catch (AccessDeniedException e) {
            throw e;
        } catch (BusinessRuleException e) {
            commonFormModel(model, null, sessionService.assignableList(user), user);
            model.addAttribute("errorMessage", e.getMessage());
            return "sessions/form";
        }
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        UserAccount user = authority.currentUser();
        ClassSession session = sessionService.getAuthorized(id, user);
        model.addAttribute("cs", session);
        model.addAttribute("attendance", attendanceRepository.findForSession(id));
        model.addAttribute("isAdmin", authority.isAdmin(user));
        return "sessions/detail";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id,
                           Model model,
                           RedirectAttributes redirect) {
        UserAccount user = authority.currentUser();
        ClassSession session = sessionService.getAuthorized(id, user);
        if (!session.isOpen()) {
            redirect.addFlashAttribute("errorMessage",
                    "Only OPEN sessions can be edited (this one is " + session.getStatus() + ").");
            return "redirect:/sessions/" + id;
        }
        model.addAttribute("cs", session);
        model.addAttribute("assignments", List.of(session.getBatchSubject()));
        model.addAttribute("isTeacher", authority.isTeacher(user));
        model.addAttribute("form", fromSession(session));
        return "sessions/form";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id,
                         @Valid @ModelAttribute("form") SessionForm form,
                         BindingResult binding,
                         Model model,
                         RedirectAttributes redirect) {
        UserAccount user = authority.currentUser();
        ClassSession session = sessionService.getAuthorized(id, user);
        if (binding.hasErrors()) {
            model.addAttribute("cs", session);
            model.addAttribute("assignments", List.of(session.getBatchSubject()));
            model.addAttribute("isTeacher", authority.isTeacher(user));
            return "sessions/form";
        }
        try {
            sessionService.update(id, form, user);
            redirect.addFlashAttribute("successMessage", "Class session updated.");
            return "redirect:/sessions/" + id;
        } catch (AccessDeniedException e) {
            throw e;
        } catch (BusinessRuleException e) {
            model.addAttribute("cs", session);
            model.addAttribute("assignments", List.of(session.getBatchSubject()));
            model.addAttribute("isTeacher", authority.isTeacher(user));
            model.addAttribute("errorMessage", e.getMessage());
            return "sessions/form";
        }
    }

    @PostMapping("/{id}/complete")
    public String complete(@PathVariable Long id, RedirectAttributes redirect) {
        try {
            sessionService.complete(id, authority.currentUser());
            redirect.addFlashAttribute("successMessage",
                    "Session completed. Teachers may no longer change its attendance; administrators can still correct it.");
        } catch (BusinessRuleException | AccessDeniedException e) {
            redirect.addFlashAttribute("errorMessage", messageOf(e));
        }
        return "redirect:/sessions/" + id;
    }

    @PostMapping("/{id}/cancel")
    public String cancel(@PathVariable Long id, RedirectAttributes redirect) {
        try {
            sessionService.cancel(id, authority.currentUser());
            redirect.addFlashAttribute("successMessage", "Session cancelled. Its record is preserved.");
        } catch (BusinessRuleException | AccessDeniedException e) {
            redirect.addFlashAttribute("errorMessage", messageOf(e));
        }
        return "redirect:/sessions/" + id;
    }

    @GetMapping("/{id}/attendance")
    public String attendancePage(@PathVariable Long id, Model model) {
        UserAccount user = authority.currentUser();
        ClassSession session = sessionService.getAuthorized(id, user);
        model.addAttribute("cs", session);
        model.addAttribute("roster", sessionService.rosterFor(session));
        model.addAttribute("isAdmin", authority.isAdmin(user));
        model.addAttribute("allStatuses", List.of(Attendance.Status.values()));
        // The page itself renders its inline "cancelled" warning below; the
        // model's errorMessage is reserved for flash messages from POST
        // redirects, which would otherwise be silently overwritten here.
        return "sessions/attendance";
    }

    /**
     * The printable attendance sheet of one session: the eligible
     * roster of that class date with the marks recorded so far, ready
     * to be printed (or saved as PDF) and marked by hand. The same
     * ownership rules apply as for the session page itself, so a
     * teacher can only print their own sessions; an administrator may
     * print any session.
     */
    @GetMapping("/{id}/print")
    public String printSheet(@PathVariable Long id, Model model) {
        UserAccount user = authority.currentUser();
        ClassSession session = sessionService.getAuthorized(id, user);
        model.addAttribute("cs", session);
        model.addAttribute("roster", sessionService.rosterFor(session));
        model.addAttribute("markColumns", List.of(
                new MarkColumn("PRESENT", "Present"),
                new MarkColumn("ABSENT", "Absent"),
                new MarkColumn("LATE", "Late"),
                new MarkColumn("EXCUSED", "Excused")));
        model.addAttribute("printedOn", businessClock.today());
        return "sessions/attendance-sheet";
    }

    @PostMapping("/{id}/attendance")
    public String saveAttendance(@PathVariable Long id,
                                 HttpServletRequest request,
                                 RedirectAttributes redirect) {
        // Rows are read from raw parameters (status_<enrollmentId> and
        // remark_<enrollmentId>): the roster is re-validated server side
        // regardless of what the browser rendered.
        List<AttendanceForm.Row> rows = new ArrayList<>();
        Map<String, String[]> params = request.getParameterMap();
        for (String key : params.keySet()) {
            String enrollmentId = stripPrefix(key, "status_");
            if (enrollmentId == null) {
                continue;
            }
            AttendanceForm.Row row = new AttendanceForm.Row();
            try {
                row.setEnrollmentId(Long.parseLong(enrollmentId));
            } catch (NumberFormatException e) {
                continue;
            }
            String status = params.get(key)[0];
            try {
                row.setStatus(Attendance.Status.valueOf(status));
            } catch (IllegalArgumentException e) {
                redirect.addFlashAttribute("errorMessage", "Invalid attendance status submitted.");
                return "redirect:/sessions/" + id + "/attendance";
            }
            String[] remarkValues = params.get("remark_" + enrollmentId);
            row.setRemarks(remarkValues == null ? "" : remarkValues[0]);
            rows.add(row);
        }
        AttendanceForm form = new AttendanceForm();
        form.setRows(rows);
        try {
            int saved = attendanceService.record(id, form, authority.currentUser());
            redirect.addFlashAttribute("successMessage", "Attendance saved for " + saved + " student(s).");
            return "redirect:/sessions/" + id;
        } catch (AccessDeniedException e) {
            throw e;
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/sessions/" + id + "/attendance";
        }
    }

    // ----- helpers ----------------------------------------------------------

    /** One marking column of the printable sheet: the status name and its label. */
    public record MarkColumn(String name, String label) {
    }

    private void commonFormModel(Model model, ClassSession session, List<?> assignments, UserAccount user) {
        model.addAttribute("cs", session);
        model.addAttribute("assignments", assignments);
        model.addAttribute("isTeacher", authority.isTeacher(user));
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new SessionForm());
        }
    }

    /** Returns the text after "status_" for keys like status_42, else null. */
    private static String stripPrefix(String key, String prefix) {
        return key != null && key.startsWith(prefix) && key.length() > prefix.length()
                ? key.substring(prefix.length())
                : null;
    }

    private Long teacherIdOf(UserAccount user) {
        Teacher teacher = authority.currentTeacher(user);
        if (teacher == null) {
            throw new AccessDeniedException("Your account has no active teacher profile.");
        }
        return teacher.getId();
    }

    private static String messageOf(Exception e) {
        if (e instanceof BusinessRuleException bre) {
            return bre.getMessage();
        }
        return "You do not have access to this operation.";
    }

    private static ClassSession.Status parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return ClassSession.Status.valueOf(status);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static SessionForm fromSession(ClassSession session) {
        SessionForm form = new SessionForm();
        form.setBatchSubjectId(session.getBatchSubject().getId());
        form.setSessionDate(session.getSessionDate());
        form.setStartTime(session.getStartTime());
        form.setEndTime(session.getEndTime());
        form.setTopic(session.getTopic() == null ? "" : session.getTopic());
        form.setNotes(session.getNotes() == null ? "" : session.getNotes());
        return form;
    }

    private static String pagerUrl(Long batchId, Long subjectId, LocalDate from, LocalDate to, String status) {
        StringBuilder url = new StringBuilder("/sessions");
        boolean first = true;
        if (batchId != null) {
            url.append("?batchId=").append(batchId);
            first = false;
        }
        if (subjectId != null) {
            url.append(first ? "?" : "&").append("subjectId=").append(subjectId);
            first = false;
        }
        if (from != null) {
            url.append(first ? "?" : "&").append("from=").append(from);
            first = false;
        }
        if (to != null) {
            url.append(first ? "?" : "&").append("to=").append(to);
            first = false;
        }
        if (status != null && !status.isBlank()) {
            url.append(first ? "?" : "&").append("status=").append(status);
        }
        return url.toString();
    }
}
