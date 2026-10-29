package com.omsai.coaching.student;

import com.omsai.coaching.common.BusinessClock;
import com.omsai.coaching.common.BusinessRuleException;
import com.omsai.coaching.examination.ExaminationResult;
import com.omsai.coaching.fees.FeeAccount;
import com.omsai.coaching.fees.InstallmentService;
import com.omsai.coaching.fees.Payment;
import com.omsai.coaching.fees.PaymentForm;
import com.omsai.coaching.fees.PaymentService;
import com.omsai.coaching.notice.Announcement;
import com.omsai.coaching.notice.NoticeService;
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
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    private final StudentLearningService learningService;
    private final PaymentService paymentService;
    private final NoticeService noticeService;
    private final UserAccountRepository userAccountRepository;
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final BusinessClock businessClock;

    public StudentPortalController(StudentPortalService portalService,
                                   StudentLearningService learningService,
                                   PaymentService paymentService,
                                   NoticeService noticeService,
                                   UserAccountRepository userAccountRepository,
                                   UserService userService,
                                   PasswordEncoder passwordEncoder,
                                   BusinessClock businessClock) {
        this.portalService = portalService;
        this.learningService = learningService;
        this.paymentService = paymentService;
        this.noticeService = noticeService;
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
        // The exam schedule, what was taught, and the latest notices:
        model.addAttribute("upcomingExams",
                portalService.upcomingExaminations(student, businessClock.today()));
        model.addAttribute("recentTopics", learningService.recentTopics(student));
        model.addAttribute("latestNotices",
                noticeService.forStudent(portalService.activeBatchIds(student))
                        .stream().limit(3).toList());
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
        Map<Long, java.math.BigDecimal> outstandings = new LinkedHashMap<>();
        for (FeeAccount account : accounts) {
            outstandings.put(account.getId(), portalService.outstandingOf(account));
        }
        model.addAttribute("student", student);
        model.addAttribute("accounts", accounts);
        model.addAttribute("outstandings", outstandings);
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

    // ==================== study resources and quizzes ====================

    /** Study resources for the subjects the student's classes cover. */
    @GetMapping("/resources")
    public String resources(Model model) {
        Student student = portalService.studentOf(currentUser());
        Map<String, List<StudyResource>> bySubject = new LinkedHashMap<>();
        for (StudyResource resource : learningService.resourcesOf(student)) {
            String subject = resource.getSubject() == null
                    ? "General"
                    : resource.getSubject().getName();
            bySubject.computeIfAbsent(subject, k -> new java.util.ArrayList<>()).add(resource);
        }
        model.addAttribute("student", student);
        model.addAttribute("bySubject", bySubject);
        return "student/resources";
    }

    /** The quizzes open to the student's batches, with past attempts. */
    @GetMapping("/quizzes")
    public String quizzes(Model model) {
        Student student = portalService.studentOf(currentUser());
        model.addAttribute("student", student);
        model.addAttribute("quizzes", learningService.quizzesOf(student));
        Map<Long, QuizAttempt> attemptByQuiz = new LinkedHashMap<>();
        for (QuizAttempt attempt : learningService.attemptsOf(student)) {
            attemptByQuiz.putIfAbsent(attempt.getQuiz().getId(), attempt);
        }
        model.addAttribute("attemptByQuiz", attemptByQuiz);
        return "student/quizzes";
    }

    /** Take one quiz (the page never carries the correct options). */
    @GetMapping("/quizzes/{id}")
    public String takeQuiz(@PathVariable Long id, Model model) {
        Student student = portalService.studentOf(currentUser());
        Quiz quiz;
        try {
            quiz = learningService.quizFor(learningService.quizOf(id), student);
        } catch (BusinessRuleException e) {
            return "redirect:/student/quizzes";
        }
        model.addAttribute("student", student);
        model.addAttribute("quiz", quiz);
        model.addAttribute("questions", learningService.questionsOf(quiz));
        return "student/quiz";
    }

    /** Submit the answers; the server scores them. */
    @PostMapping("/quizzes/{id}")
    public String submitQuiz(@PathVariable Long id,
                             @ModelAttribute("answerForm") QuizAnswersForm answerForm,
                             RedirectAttributes redirect) {
        Student student = portalService.studentOf(currentUser());
        Quiz quiz;
        try {
            quiz = learningService.quizFor(learningService.quizOf(id), student);
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute("errorMessage",
                    "That quiz does not belong to your batches.");
            return "redirect:/student/quizzes";
        }
        QuizAttempt attempt;
        try {
            attempt = learningService.submitAttempt(
                    quiz, student, currentUser(),
                    answerForm.getAnswers() == null ? List.of() : answerForm.getAnswers());
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/student/quizzes";
        }
        redirect.addFlashAttribute("successMessage",
                "You scored " + attempt.getCorrectCount() + " of " + attempt.getTotalCount()
                        + " (" + attempt.percentage() + "%).");
        return "redirect:/student/quizzes/" + quiz.getId() + "/result";
    }

    /** The review of the student's own submission for one quiz. */
    @GetMapping("/quizzes/{id}/result")
    public String quizResult(@PathVariable Long id, Model model,
                             RedirectAttributes redirect) {
        Student student = portalService.studentOf(currentUser());
        Quiz quiz;
        try {
            quiz = learningService.quizFor(learningService.quizOf(id), student);
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute("errorMessage",
                    "That quiz does not belong to your batches.");
            return "redirect:/student/quizzes";
        }
        QuizAttempt attempt = learningService.attemptFor(quiz, student);
        if (attempt == null) {
            redirect.addFlashAttribute("errorMessage",
                    "You have not attempted this quiz yet.");
            return "redirect:/student/quizzes/" + id;
        }
        boolean reviewAvailable = attempt.getAnswers() != null
                && !attempt.getAnswers().isBlank();
        model.addAttribute("student", student);
        model.addAttribute("quiz", quiz);
        model.addAttribute("attempt", attempt);
        model.addAttribute("reviewAvailable", reviewAvailable);
        model.addAttribute("review",
                reviewAvailable ? learningService.reviewOf(quiz, attempt) : List.of());
        return "student/quiz-result";
    }

    // ==================== the notice board ====================

    /** The notices addressed to this student (or to everybody). */
    @GetMapping("/notices")
    public String notices(Model model) {
        UserAccount account = currentUser();
        Student student = portalService.studentOf(account);
        List<Long> batchIds = portalService.activeBatchIds(student);
        List<Announcement> notices = noticeService.forStudent(batchIds);
        // What was unread is remembered for the page highlight, and
        // then everything shown is marked as read.
        Set<Long> unreadIds = noticeService.unreadIds(batchIds, account.getId());
        noticeService.markRead(notices.stream().map(Announcement::getId).toList(),
                account.getId());
        model.addAttribute("student", student);
        model.addAttribute("notices", notices);
        model.addAttribute("unreadIds", unreadIds);
        return "student/notices";
    }

    // ==================== own payment receipts ====================

    /**
     * The printable reissue of one of the student's OWN receipts. The
     * ownership check is the same one as the pay flow: the payment
     * must sit on a fee agreement of this student, or the request is
     * denied.
     */
    @GetMapping("/receipts/{id}")
    public String receipt(@PathVariable Long id, Model model) {
        Student student = portalService.studentOf(currentUser());
        Payment payment = paymentService.getWithDetails(id);
        Long owner = payment.getFeeAccount().getStudent().getId();
        if (!owner.equals(student.getId())) {
            throw new AccessDeniedException("That receipt does not belong to you.");
        }
        model.addAttribute("payment", payment);
        model.addAttribute("outstandingNow",
                portalService.outstandingOf(payment.getFeeAccount()));
        return "student/receipt";
    }

    // ==================== paying fees from the portal ====================

    /**
     * The payment form for one of the student's OWN fee agreements. The
     * outstanding balance always comes from the server; the student
     * cannot choose an account or an amount that exceeds it.
     */
    @GetMapping("/pay")
    public String payForm(@RequestParam Long feeAccountId, Model model) {
        Student student = portalService.studentOf(currentUser());
        FeeAccount account = ownedAccount(student, feeAccountId);
        BigDecimal outstanding = portalService.outstandingOf(account);
        List<InstallmentService.InstallmentView> installments =
                portalService.installmentsOf(account);
        model.addAttribute("student", student);
        model.addAttribute("account", account);
        model.addAttribute("outstanding", outstanding);
        model.addAttribute("nextInstallment", firstDue(installments));
        model.addAttribute("methods", List.of(Payment.Method.CASH,
                Payment.Method.UPI, Payment.Method.BANK_TRANSFER, Payment.Method.CARD));
        model.addAttribute("today", businessClock.today());
        return "student/pay";
    }

    /**
     * Record a payment against the student's own agreement. The same
     * validation, allocation and balance rules as the office screens
     * apply (PaymentsService.record). The sign-in account is recorded
     * as the recorder — the payment trail stays honest.
     */
    @PostMapping("/pay")
    public String pay(@RequestParam Long feeAccountId,
                      @RequestParam String amount,
                      @RequestParam Payment.Method paymentMethod,
                      @RequestParam(required = false) String referenceNumber,
                      @RequestParam(required = false) String remarks,
                      RedirectAttributes redirect) {
        Student student = portalService.studentOf(currentUser());
        FeeAccount owned = ownedAccount(student, feeAccountId);

        BigDecimal parsed;
        try {
            parsed = new BigDecimal(amount.trim());
        } catch (NumberFormatException e) {
            redirect.addFlashAttribute("errorMessage", "Enter the amount in numbers, for example 12000.");
            return "redirect:/student/pay?feeAccountId=" + owned.getId();
        }
        if (parsed.signum() <= 0) {
            redirect.addFlashAttribute("errorMessage", "The amount must be greater than zero.");
            return "redirect:/student/pay?feeAccountId=" + owned.getId();
        }
        if (paymentMethod != Payment.Method.CASH
                && (referenceNumber == null || referenceNumber.isBlank())) {
            redirect.addFlashAttribute("errorMessage",
                    "For " + paymentMethod + " payments the reference number is required.");
            return "redirect:/student/pay?feeAccountId=" + owned.getId();
        }

        PaymentForm form = new PaymentForm();
        form.setFeeAccountId(owned.getId());
        form.setAmount(parsed);
        form.setPaymentDate(businessClock.today());
        form.setPaymentMethod(paymentMethod);
        form.setReferenceNumber(referenceNumber);
        form.setRemarks(remarks == null || remarks.isBlank()
                ? "Recorded by the student from the portal."
                : remarks.strip());
        try {
            Payment payment = paymentService.record(form, currentUser());
            redirect.addFlashAttribute("successMessage",
                    "Thank you. Payment " + payment.getReceiptNumber() + " of ₹ "
                            + parsed.toPlainString() + " is recorded and allocated to your "
                            + "earliest dues. The office can void it with a reason if needed.");
            return "redirect:/student/fees";
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/student/pay?feeAccountId=" + owned.getId();
        }
    }


    // ----- helpers ----------------------------------------------------------

    /** The fee agreement of THIS student, or an access denied. */
    private FeeAccount ownedAccount(Student student, Long feeAccountId) {
        return portalService.feeAccountsOf(student).stream()
                .filter(a -> a.getId().equals(feeAccountId))
                .findFirst()
                .orElseThrow(() -> new AccessDeniedException(
                        "That fee agreement does not belong to you."));
    }

    /** The installment due soonest that still has a balance, or null. */
    private InstallmentService.InstallmentView firstDue(
            List<InstallmentService.InstallmentView> installments) {
        return installments.stream()
                .filter(v -> v.remaining().signum() > 0)
                .min(java.util.Comparator.comparing(v -> v.installment().getDueDate()))
                .orElse(null);
    }


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

    /**
     * The option letters posted by the quiz form, one entry per question
     * position ({@code answers[0]}, {@code answers[1]}, …). An
     * unanswered question sends nothing, which binds as a null entry.
     */
    public static class QuizAnswersForm {

        private List<String> answers;

        public List<String> getAnswers() {
            return answers;
        }

        public void setAnswers(List<String> answers) {
            this.answers = answers;
        }
    }
}
