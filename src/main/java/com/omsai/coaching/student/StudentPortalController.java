package com.omsai.coaching.student;

import com.omsai.coaching.common.BusinessClock;
import com.omsai.coaching.common.BusinessRuleException;
import com.omsai.coaching.examination.ExaminationResult;
import com.omsai.coaching.fees.FeeAccount;
import com.omsai.coaching.fees.InstallmentService;
import com.omsai.coaching.fees.Payment;
import com.omsai.coaching.security.AppUserPrincipal;
import com.omsai.coaching.security.UserAccount;
import com.omsai.coaching.security.UserAccountRepository;
import com.omsai.coaching.security.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.List;

/**
 * The student portal.
 *
 * <p>Every page is derived from the account that is signed in: there
 * is no student id in the URL, so a student can never reach another
 * student's data. Students see their own information only and cannot
 * change attendance, fees, payments or marks.</p>
 */
@Controller
@RequestMapping("/student")
@PreAuthorize("hasRole('STUDENT')")
public class StudentPortalController {

    private final StudentPortalService portalService;
    private final UserAccountRepository userAccountRepository;
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final BusinessClock businessClock;

    public StudentPortalController(StudentPortalService portalService,
                                   UserAccountRepository userAccountRepository,
                                   UserService userService,
                                   PasswordEncoder passwordEncoder,
                                   BusinessClock businessClock) {
        this.portalService = portalService;
        this.userAccountRepository = userAccountRepository;
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
        this.businessClock = businessClock;
    }

    /** The student's dashboard. */
    @GetMapping
    public String dashboard(Model model) {
        Student student = portalService.studentOf(currentUser());
        var enrollment = portalService.currentEnrollment(student);
        var report = portalService.attendanceOf(student);
        List<FeeAccount> accounts = portalService.feeAccountsOf(student);
        BigDecimalSummary outstanding = outstandingOf(accounts);
        List<ExaminationResult> results = portalService.publishedResults(student);

        model.addAttribute("student", student);
        model.addAttribute("enrollment", enrollment);
        model.addAttribute("attendance", report);
        model.addAttribute("attendancePercentage", report.overallPercentage());
        model.addAttribute("accounts", accounts);
        model.addAttribute("outstanding", outstanding.total());
        model.addAttribute("results", results);
        model.addAttribute("todayClasses", portalService.timetableOn(student, businessClock.today()));
        model.addAttribute("nextInstallments", nextInstallments(accounts));
        model.addAttribute("today", businessClock.today());
        return "student/dashboard";
    }

    /** The student's own timetable. */
    @GetMapping("/timetable")
    public String timetable(Model model) {
        Student student = portalService.studentOf(currentUser());
        LocalDate today = businessClock.today();
        model.addAttribute("student", student);
        model.addAttribute("today", today);
        model.addAttribute("todayClasses", portalService.timetableOn(student, today));
        model.addAttribute("weekClasses", portalService.timetableOf(student));
        return "student/timetable";
    }

    /** The student's own attendance history. */
    @GetMapping("/attendance")
    public String attendance(Model model) {
        Student student = portalService.studentOf(currentUser());
        var report = portalService.attendanceOf(student);
        model.addAttribute("student", student);
        model.addAttribute("report", report);
        model.addAttribute("percentage", report.overallPercentage());
        return "student/attendance";
    }

    /** The student's own fees, installments and payments. */
    @GetMapping("/fees")
    public String fees(Model model) {
        Student student = portalService.studentOf(currentUser());
        List<FeeAccount> accounts = portalService.feeAccountsOf(student);
        model.addAttribute("student", student);
        model.addAttribute("accounts", accounts);
        model.addAttribute("installments", installmentsOf(accounts));
        model.addAttribute("payments", paymentsOf(accounts));
        model.addAttribute("outstanding", outstandingOf(accounts).total());
        return "student/fees";
    }

    /** The student's own published results. */
    @GetMapping("/results")
    public String results(Model model) {
        Student student = portalService.studentOf(currentUser());
        model.addAttribute("student", student);
        model.addAttribute("results", portalService.publishedResults(student));
        return "student/results";
    }

    /** The student's own profile. */
    @GetMapping("/profile")
    public String profile(Model model) {
        Student student = portalService.studentOf(currentUser());
        model.addAttribute("student", student);
        model.addAttribute("enrollments", portalService.enrollmentsOf(student));
        return "student/profile";
    }

    @PostMapping("/profile")
    public String updateProfile(@RequestParam(required = false) String phone,
                                @RequestParam(required = false) String parentName,
                                @RequestParam(required = false) String parentPhone,
                                @RequestParam(required = false) String address,
                                RedirectAttributes redirect) {
        Student student = portalService.studentOf(currentUser());
        portalService.updateProfile(student, phone, parentName, parentPhone, address);
        redirect.addFlashAttribute("successMessage", "Profile updated.");
        return "redirect:/student/profile";
    }

    /** The student changes their own password. */
    @PostMapping("/password")
    public String changePassword(@RequestParam String currentPassword,
                                 @RequestParam String newPassword,
                                 @RequestParam String confirmPassword,
                                 RedirectAttributes redirect) {
        UserAccount account = currentUser();
        if (!passwordEncoder.matches(currentPassword, account.getPasswordHash())) {
            redirect.addFlashAttribute("errorMessage", "The current password is not correct.");
            return "redirect:/student/profile";
        }
        if (!newPassword.equals(confirmPassword)) {
            redirect.addFlashAttribute("errorMessage", "The new passwords do not match.");
            return "redirect:/student/profile";
        }
        if (newPassword.length() < 12) {
            redirect.addFlashAttribute("errorMessage",
                    "The new password must be at least 12 characters.");
            return "redirect:/student/profile";
        }
        userService.resetPassword(account.getId(), newPassword);
        redirect.addFlashAttribute("successMessage", "Password changed.");
        return "redirect:/student/profile";
    }

    // ----- helpers ----------------------------------------------------------

    /** The sum of the outstanding balances of the student's agreements. */
    private record BigDecimalSummary(java.math.BigDecimal total) {
    }

    private BigDecimalSummary outstandingOf(List<FeeAccount> accounts) {
        java.math.BigDecimal total = java.math.BigDecimal.ZERO;
        for (FeeAccount account : accounts) {
            total = total.add(portalService.outstandingOf(account));
        }
        return new BigDecimalSummary(total);
    }

    /** The installments that still have a balance, soonest first. */
    private List<InstallmentService.InstallmentView> nextInstallments(List<FeeAccount> accounts) {
        List<InstallmentService.InstallmentView> open = new java.util.ArrayList<>();
        for (FeeAccount account : accounts) {
            for (InstallmentService.InstallmentView view : portalService.installmentsOf(account)) {
                if (view.remaining().signum() > 0) {
                    open.add(view);
                }
            }
        }
        open.sort(java.util.Comparator.comparing(
                view -> view.installment().getDueDate()));
        return open.stream().limit(5).toList();
    }

    private List<InstallmentService.InstallmentView> installmentsOf(List<FeeAccount> accounts) {
        List<InstallmentService.InstallmentView> views = new java.util.ArrayList<>();
        for (FeeAccount account : accounts) {
            views.addAll(portalService.installmentsOf(account));
        }
        return views;
    }

    private List<Payment> paymentsOf(List<FeeAccount> accounts) {
        List<Payment> payments = new java.util.ArrayList<>();
        for (FeeAccount account : accounts) {
            payments.addAll(portalService.paymentsOf(account));
        }
        return payments;
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
}
