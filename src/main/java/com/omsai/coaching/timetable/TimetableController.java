package com.omsai.coaching.timetable;

import com.omsai.coaching.batch.BatchRepository;
import com.omsai.coaching.batch.BatchSubjectRepository;
import com.omsai.coaching.common.BusinessClock;
import com.omsai.coaching.common.BusinessRuleException;
import com.omsai.coaching.security.AppUserPrincipal;
import com.omsai.coaching.security.UserAccount;
import com.omsai.coaching.security.UserAccountRepository;
import com.omsai.coaching.teacher.Teacher;
import com.omsai.coaching.teacher.TeacherRepository;
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

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

/**
 * Timetable screens: the recurring schedules of the institute, the
 * weekly view, the calendar exceptions and the generation of class
 * sessions.
 *
 * <p>Administrators manage the whole timetable; teachers see the
 * schedules of their own assignments. Teachers never reach the
 * management actions - the class-level check and the URL rules both
 * apply.</p>
 */
@Controller
@RequestMapping("/timetable")
public class TimetableController {

    private final TimetableService timetableService;
    private final BatchSubjectRepository batchSubjectRepository;
    private final BatchRepository batchRepository;
    private final TeacherRepository teacherRepository;
    private final UserAccountRepository userAccountRepository;
    private final BusinessClock businessClock;

    public TimetableController(TimetableService timetableService,
                               BatchSubjectRepository batchSubjectRepository,
                               BatchRepository batchRepository,
                               TeacherRepository teacherRepository,
                               UserAccountRepository userAccountRepository,
                               BusinessClock businessClock) {
        this.timetableService = timetableService;
        this.batchSubjectRepository = batchSubjectRepository;
        this.batchRepository = batchRepository;
        this.teacherRepository = teacherRepository;
        this.userAccountRepository = userAccountRepository;
        this.businessClock = businessClock;
    }

    /** The weekly timetable with the filters. */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public String weekly(@RequestParam(required = false) LocalDate week,
                         @RequestParam(required = false) Long batchId,
                         @RequestParam(required = false) Long teacherId,
                         @RequestParam(required = false) String classroom,
                         Model model) {
        UserAccount user = currentUser();
        boolean teacher = user.getRole() == UserAccount.Role.TEACHER;
        LocalDate weekStart = week == null ? businessClock.today() : week;

        List<TimetableEntry> entries;
        if (teacher) {
            Teacher profile = teacherRepository.findByLinkedUserId(user.getId()).orElse(null);
            entries = profile == null ? List.of() : timetableService.forTeacher(profile.getId());
        } else {
            entries = timetableService.all();
        }
        if (batchId != null) {
            entries = entries.stream()
                    .filter(entry -> entry.getBatchSubject().getBatch().getId().equals(batchId))
                    .toList();
        }
        if (teacherId != null) {
            entries = entries.stream()
                    .filter(entry -> entry.getBatchSubject().getTeacher() != null
                            && entry.getBatchSubject().getTeacher().getId().equals(teacherId))
                    .toList();
        }
        if (classroom != null && !classroom.isBlank()) {
            entries = entries.stream()
                    .filter(entry -> classroom.equalsIgnoreCase(entry.getClassroom()))
                    .toList();
        }

        model.addAttribute("weekStart", weekStart);
        model.addAttribute("days", daysOf(weekStart));
        model.addAttribute("entries", entries);
        model.addAttribute("batches", batchRepository.findAll());
        model.addAttribute("teachers", teacherRepository.findAll());
        model.addAttribute("batchId", batchId);
        model.addAttribute("teacherId", teacherId);
        model.addAttribute("classroom", classroom);
        model.addAttribute("isTeacher", teacher);
        model.addAttribute("today", businessClock.today());
        return "timetable/weekly";
    }

    /** The form of a new recurring schedule. */
    @GetMapping("/new")
    @PreAuthorize("hasRole('ADMIN')")
    public String newForm(Model model) {
        model.addAttribute("form", new TimetableForm());
        model.addAttribute("assignments", batchSubjectRepository.findAllWithContext());
        model.addAttribute("days", DayOfWeek.values());
        return "timetable/form";
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public String create(@Valid @ModelAttribute("form") TimetableForm form,
                         BindingResult binding,
                         Model model,
                         RedirectAttributes redirect) {
        if (binding.hasErrors()) {
            model.addAttribute("assignments", batchSubjectRepository.findAllWithContext());
            model.addAttribute("days", DayOfWeek.values());
            return "timetable/form";
        }
        try {
            TimetableEntry entry = timetableService.create(form, currentUser());
            redirect.addFlashAttribute("successMessage", "Timetable entry created.");
            return "redirect:/timetable/" + entry.getId();
        } catch (AccessDeniedException e) {
            throw e;
        } catch (BusinessRuleException e) {
            model.addAttribute("assignments", batchSubjectRepository.findAllWithContext());
            model.addAttribute("days", DayOfWeek.values());
            model.addAttribute("errorMessage", e.getMessage());
            return "timetable/form";
        }
    }

    /** The detail of one schedule. */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    public String detail(@PathVariable Long id, Model model) {
        UserAccount user = currentUser();
        TimetableEntry entry = timetableService.get(id);
        if (user.getRole() == UserAccount.Role.TEACHER) {
            Teacher profile = teacherRepository.findByLinkedUserId(user.getId()).orElse(null);
            boolean own = profile != null && entry.getBatchSubject().getTeacher() != null
                    && entry.getBatchSubject().getTeacher().getId().equals(profile.getId());
            if (!own) {
                throw new AccessDeniedException("You do not have access to this timetable entry.");
            }
        }
        model.addAttribute("entry", entry);
        model.addAttribute("isTeacher", user.getRole() == UserAccount.Role.TEACHER);
        return "timetable/detail";
    }

    @GetMapping("/{id}/edit")
    @PreAuthorize("hasRole('ADMIN')")
    public String editForm(@PathVariable Long id, Model model) {
        TimetableEntry entry = timetableService.get(id);
        model.addAttribute("entry", entry);
        model.addAttribute("form", fromEntry(entry));
        model.addAttribute("assignments", batchSubjectRepository.findAllWithContext());
        model.addAttribute("days", DayOfWeek.values());
        return "timetable/form";
    }

    @PostMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public String update(@PathVariable Long id,
                         @Valid @ModelAttribute("form") TimetableForm form,
                         BindingResult binding,
                         Model model,
                         RedirectAttributes redirect) {
        if (binding.hasErrors()) {
            model.addAttribute("entry", timetableService.get(id));
            model.addAttribute("assignments", batchSubjectRepository.findAllWithContext());
            model.addAttribute("days", DayOfWeek.values());
            return "timetable/form";
        }
        try {
            timetableService.update(id, form);
            redirect.addFlashAttribute("successMessage", "Timetable entry updated.");
            return "redirect:/timetable/" + id;
        } catch (AccessDeniedException e) {
            throw e;
        } catch (BusinessRuleException e) {
            model.addAttribute("entry", timetableService.get(id));
            model.addAttribute("assignments", batchSubjectRepository.findAllWithContext());
            model.addAttribute("days", DayOfWeek.values());
            model.addAttribute("errorMessage", e.getMessage());
            return "timetable/form";
        }
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public String deactivate(@PathVariable Long id, RedirectAttributes redirect) {
        try {
            timetableService.deactivate(id);
            redirect.addFlashAttribute("successMessage",
                    "Timetable entry deactivated. The classes it generated are kept.");
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/timetable/" + id;
    }

    // ----- calendar exceptions ---------------------------------------------

    @GetMapping("/exceptions")
    @PreAuthorize("hasRole('ADMIN')")
    public String exceptions(@RequestParam(required = false) LocalDate from,
                             @RequestParam(required = false) LocalDate to,
                             Model model) {
        LocalDate today = businessClock.today();
        LocalDate effectiveFrom = from == null ? today.minusDays(30) : from;
        LocalDate effectiveTo = to == null ? today.plusDays(90) : to;
        model.addAttribute("exceptions", timetableService.exceptionsBetween(effectiveFrom, effectiveTo));
        model.addAttribute("entries", timetableService.all());
        model.addAttribute("from", effectiveFrom);
        model.addAttribute("to", effectiveTo);
        model.addAttribute("form", new CalendarExceptionForm());
        model.addAttribute("kinds", CalendarException.Kind.values());
        return "timetable/exceptions";
    }

    @PostMapping("/exceptions")
    @PreAuthorize("hasRole('ADMIN')")
    public String addException(@Valid @ModelAttribute("form") CalendarExceptionForm form,
                                BindingResult binding,
                                Model model,
                                RedirectAttributes redirect) {
        if (binding.hasErrors()) {
            model.addAttribute("exceptions", timetableService.exceptionsBetween(
                    businessClock.today().minusDays(30), businessClock.today().plusDays(90)));
            model.addAttribute("entries", timetableService.all());
            model.addAttribute("from", businessClock.today().minusDays(30));
            model.addAttribute("to", businessClock.today().plusDays(90));
            model.addAttribute("kinds", CalendarException.Kind.values());
            return "timetable/exceptions";
        }
        try {
            timetableService.addException(form, currentUser());
            redirect.addFlashAttribute("successMessage", "Calendar exception recorded.");
            return "redirect:/timetable/exceptions";
        } catch (AccessDeniedException e) {
            throw e;
        } catch (BusinessRuleException e) {
            model.addAttribute("exceptions", timetableService.exceptionsBetween(
                    businessClock.today().minusDays(30), businessClock.today().plusDays(90)));
            model.addAttribute("entries", timetableService.all());
            model.addAttribute("from", businessClock.today().minusDays(30));
            model.addAttribute("to", businessClock.today().plusDays(90));
            model.addAttribute("kinds", CalendarException.Kind.values());
            model.addAttribute("errorMessage", e.getMessage());
            return "timetable/exceptions";
        }
    }

    @PostMapping("/exceptions/{id}/delete")
    @PreAuthorize("hasRole('ADMIN')")
    public String deleteException(@PathVariable Long id, RedirectAttributes redirect) {
        try {
            timetableService.removeException(id);
            redirect.addFlashAttribute("successMessage", "Calendar exception removed.");
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/timetable/exceptions";
    }

    // ----- generating class sessions ---------------------------------------

    @GetMapping("/generate")
    @PreAuthorize("hasRole('ADMIN')")
    public String generateForm(@RequestParam(required = false) LocalDate from,
                                @RequestParam(required = false) LocalDate to,
                                Model model) {
        LocalDate today = businessClock.today();
        model.addAttribute("from", from == null ? today : from);
        model.addAttribute("to", to == null ? today.plusDays(30) : to);
        return "timetable/generate";
    }

    @PostMapping("/generate")
    @PreAuthorize("hasRole('ADMIN')")
    public String generate(@RequestParam(required = false) LocalDate from,
                           @RequestParam(required = false) LocalDate to,
                           RedirectAttributes redirect) {
        try {
            TimetableService.GenerationResult result = timetableService.generateSessions(from, to, currentUser());
            redirect.addFlashAttribute("successMessage",
                    "Generated " + result.created() + " class session(s); " + result.skippedExisting()
                            + " already existed, " + result.skippedHoliday() + " holiday(s) and "
                            + result.skippedCancelled() + " cancelled occurrence(s) skipped.");
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/timetable/generate?from=" + from + "&to=" + to;
    }

    // ----- helpers ----------------------------------------------------------

    private UserAccount currentUser() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication != null
                && authentication.getPrincipal() instanceof AppUserPrincipal principal)) {
            throw new AccessDeniedException("Not authenticated.");
        }
        return userAccountRepository.findById(principal.id())
                .orElseThrow(() -> new AccessDeniedException("Your account no longer exists."));
    }

    /** The seven days of a week, starting at the given date. */
    private static List<LocalDate> daysOf(LocalDate weekStart) {
        LocalDate monday = weekStart.with(java.time.DayOfWeek.MONDAY);
        return java.util.stream.IntStream.range(0, 7)
                .mapToObj(i -> monday.plusDays(i))
                .toList();
    }

    private static TimetableForm fromEntry(TimetableEntry entry) {
        TimetableForm form = new TimetableForm();
        form.setBatchSubjectId(entry.getBatchSubject().getId());
        form.setDayOfWeek(entry.getDayOfWeek());
        form.setStartTime(entry.getStartTime());
        form.setEndTime(entry.getEndTime());
        form.setClassroom(entry.getClassroom());
        form.setTopic(entry.getTopic());
        form.setEffectiveFrom(entry.getEffectiveFrom());
        form.setEffectiveUntil(entry.getEffectiveUntil());
        form.setActive(entry.isActive());
        return form;
    }
}
