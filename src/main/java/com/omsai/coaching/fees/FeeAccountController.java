package com.omsai.coaching.fees;

import com.omsai.coaching.common.BusinessClock;
import com.omsai.coaching.common.BusinessRuleException;
import com.omsai.coaching.course.CourseRepository;
import com.omsai.coaching.enrollment.EnrollmentRepository;
import com.omsai.coaching.security.AppUserPrincipal;
import com.omsai.coaching.security.UserAccount;
import com.omsai.coaching.security.UserAccountRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Fee-agreement pages, outstanding/overdue lists and the collection
 * summary. Administrator only: teachers never reach these routes
 * (Spring Security URL rules plus this class-level check).
 */
@Controller
@RequestMapping("/fees")
@PreAuthorize("hasRole('ADMIN')")
public class FeeAccountController {

    private final FeeAccountService feeAccountService;
    private final PaymentService paymentService;
    private final InstallmentService installmentService;
    private final EnrollmentRepository enrollmentRepository;
    private final CourseRepository courseRepository;
    private final UserAccountRepository userAccountRepository;
    private final PaymentRepository paymentRepository;
    private final BusinessClock businessClock;

    public FeeAccountController(FeeAccountService feeAccountService,
                                PaymentService paymentService,
                                InstallmentService installmentService,
                                EnrollmentRepository enrollmentRepository,
                                CourseRepository courseRepository,
                                UserAccountRepository userAccountRepository,
                                PaymentRepository paymentRepository,
                                BusinessClock businessClock) {
        this.feeAccountService = feeAccountService;
        this.paymentService = paymentService;
        this.installmentService = installmentService;
        this.enrollmentRepository = enrollmentRepository;
        this.courseRepository = courseRepository;
        this.userAccountRepository = userAccountRepository;
        this.paymentRepository = paymentRepository;
        this.businessClock = businessClock;
    }

    /** All fee agreements with search and filters. */
    @GetMapping
    public String list(@RequestParam(required = false) String q,
                       @RequestParam(required = false) Long courseId,
                       @RequestParam(required = false) String academicYear,
                       @RequestParam(defaultValue = "0") int page,
                       Model model) {
        model.addAttribute("accounts",
                feeAccountService.search(0, courseId, academicYear, q, PageRequest.of(page, 20)));
        model.addAttribute("courses", courseRepository.findAll());
        model.addAttribute("q", q);
        model.addAttribute("courseId", courseId);
        model.addAttribute("academicYear", academicYear);
        model.addAttribute("mode", "all");
        model.addAttribute("pagerUrl", pagerUrl("/fees", q, courseId, academicYear));
        return "fees/list";
    }

    /** Accounts with a positive outstanding balance. */
    @GetMapping("/outstanding")
    public String outstanding(@RequestParam(required = false) String q,
                              @RequestParam(required = false) Long courseId,
                              @RequestParam(required = false) String academicYear,
                              @RequestParam(defaultValue = "0") int page,
                              Model model) {
        model.addAttribute("accounts",
                feeAccountService.search(1, courseId, academicYear, q, PageRequest.of(page, 20)));
        model.addAttribute("courses", courseRepository.findAll());
        model.addAttribute("q", q);
        model.addAttribute("courseId", courseId);
        model.addAttribute("academicYear", academicYear);
        model.addAttribute("mode", "outstanding");
        model.addAttribute("pagerUrl", pagerUrl("/fees/outstanding", q, courseId, academicYear));
        return "fees/list";
    }

    /** Accounts overdue: due date passed, balance still outstanding. */
    @GetMapping("/overdue")
    public String overdue(@RequestParam(required = false) String q,
                          @RequestParam(required = false) Long courseId,
                          @RequestParam(required = false) String academicYear,
                          @RequestParam(defaultValue = "0") int page,
                          Model model) {
        model.addAttribute("accounts",
                feeAccountService.search(2, courseId, academicYear, q, PageRequest.of(page, 20)));
        model.addAttribute("courses", courseRepository.findAll());
        model.addAttribute("q", q);
        model.addAttribute("courseId", courseId);
        model.addAttribute("academicYear", academicYear);
        model.addAttribute("mode", "overdue");
        model.addAttribute("pagerUrl", pagerUrl("/fees/overdue", q, courseId, academicYear));
        return "fees/list";
    }

    /** New-agreement form; the enrollment is chosen from the student page. */
    @GetMapping("/new")
    public String newForm(@RequestParam Long enrollmentId, Model model) {
        var enrollment = enrollmentRepository.findWithDetails(enrollmentId).orElse(null);
        if (enrollment == null) {
            model.addAttribute("errorMessage", "Enrollment not found.");
            return "error/error";
        }
        FeeAccountForm form = new FeeAccountForm();
        form.setEnrollmentId(enrollmentId);
        form.setAcademicYear(enrollment.getBatch().getAcademicYear());
        form.setDiscountAmount(BigDecimal.ZERO);
        model.addAttribute("form", form);
        model.addAttribute("enrollment", enrollment);
        return "fees/form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("form") FeeAccountForm form,
                         BindingResult binding,
                         Model model,
                         RedirectAttributes redirect) {
        var enrollment = enrollmentRepository.findWithDetails(form.getEnrollmentId()).orElse(null);
        if (binding.hasErrors()) {
            model.addAttribute("enrollment", enrollment);
            return "fees/form";
        }
        try {
            FeeAccount account = feeAccountService.create(form, admin());
            redirect.addFlashAttribute("successMessage", "Fee agreement created.");
            return "redirect:/fees/" + account.getId();
        } catch (AccessDeniedException e) {
            throw e;
        } catch (BusinessRuleException e) {
            model.addAttribute("enrollment", enrollment);
            model.addAttribute("errorMessage", e.getMessage());
            return "fees/form";
        }
    }

    /** The complete fee account: terms, payments, receipts and the balance. */
    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        FeeAccount account = feeAccountService.getWithDetails(id);
        BigDecimal outstanding = feeAccountService.outstandingOf(account);
        List<InstallmentService.InstallmentView> installments = installmentService.viewFor(id);
        model.addAttribute("account", account);
        model.addAttribute("received", account.netFee().subtract(outstanding));
        model.addAttribute("outstanding", outstanding);
        model.addAttribute("payments", paymentRepository.findForAccount(id));
        model.addAttribute("feeStatus", feeStatus(account, outstanding));
        model.addAttribute("overdue",
                account.getDueDate() != null
                        && account.getDueDate().isBefore(businessClock.today())
                        && outstanding.compareTo(BigDecimal.ZERO) > 0);
        model.addAttribute("installments", installments);
        model.addAttribute("hasPlan", !installments.isEmpty());
        model.addAttribute("installmentSummary", installmentSummary(installments));
        // A schedule may only be replaced while no payment has been
        // booked against it.
        model.addAttribute("planEditable", installmentService.isEditable(id));
        return "fees/detail";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        FeeAccount account = feeAccountService.getWithDetails(id);
        model.addAttribute("account", account);
        model.addAttribute("hasPayments", feeAccountService.outstandingOf(account)
                .compareTo(account.netFee()) != 0);
        model.addAttribute("form", fromAccount(account));
        return "fees/form";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id,
                         @Valid @ModelAttribute("form") FeeAccountForm form,
                         BindingResult binding,
                         Model model,
                         RedirectAttributes redirect) {
        FeeAccount account = feeAccountService.getWithDetails(id);
        form.setEnrollmentId(account.getEnrollment().getId());
        if (binding.hasErrors()) {
            model.addAttribute("account", account);
            model.addAttribute("hasPayments", feeAccountService.outstandingOf(account)
                    .compareTo(account.netFee()) != 0);
            return "fees/form";
        }
        try {
            feeAccountService.update(id, form);
            redirect.addFlashAttribute("successMessage", "Fee agreement updated.");
            return "redirect:/fees/" + id;
        } catch (AccessDeniedException e) {
            throw e;
        } catch (BusinessRuleException e) {
            model.addAttribute("account", account);
            model.addAttribute("hasPayments", feeAccountService.outstandingOf(account)
                    .compareTo(account.netFee()) != 0);
            model.addAttribute("errorMessage", e.getMessage());
            return "fees/form";
        }
    }

    /**
     * The installment plan form of a fee account. The plan divides the
     * agreed net fee; the fee account itself is never changed by it.
     */
    @GetMapping("/{id}/installments")
    public String installmentForm(@PathVariable Long id,
                                  Model model,
                                  RedirectAttributes redirect) {
        FeeAccount account = feeAccountService.getWithDetails(id);
        if (installmentService.hasPlan(id)) {
            redirect.addFlashAttribute("errorMessage",
                    "This fee agreement already has an installment plan.");
            return "redirect:/fees/" + id;
        }
        installmentPlanModel(model, account);
        model.addAttribute("editing", false);
        model.addAttribute("rows", defaultRows(account));
        return "fees/installment-form";
    }

    /**
     * Creates the installment plan. The submitted rows are validated
     * server side; the whole plan (and the allocation of payments that
     * were already received) is written in one transaction.
     */
    @PostMapping("/{id}/installments")
    public String createInstallmentPlan(@PathVariable Long id,
                                        HttpServletRequest request,
                                        Model model,
                                        RedirectAttributes redirect) {
        List<InstallmentInput> rows = readRows(request.getParameterMap());
        try {
            installmentService.createPlan(id, rows, admin());
            redirect.addFlashAttribute("successMessage", "Installment plan created.");
            return "redirect:/fees/" + id;
        } catch (AccessDeniedException e) {
            throw e;
        } catch (BusinessRuleException e) {
            FeeAccount account = feeAccountService.getWithDetails(id);
            installmentPlanModel(model, account);
            model.addAttribute("editing", false);
            model.addAttribute("rows", rows);
            model.addAttribute("errorMessage", e.getMessage());
            return "fees/installment-form";
        }
    }

    /**
     * The edit form of an existing installment plan. The current
     * schedule is loaded into the form so it can be corrected.
     *
     * <p>A plan that carries payment-allocation history is immutable:
     * the schedule has become part of the financial record. The form is
     * not offered then - the fee account page explains why.</p>
     */
    @GetMapping("/{id}/installments/edit")
    public String editInstallmentPlanForm(@PathVariable Long id,
                                          Model model,
                                          RedirectAttributes redirect) {
        FeeAccount account = feeAccountService.findWithDetails(id);
        if (account == null) {
            redirect.addFlashAttribute("errorMessage", "Fee agreement not found.");
            return "redirect:/fees";
        }
        if (!installmentService.hasPlan(id)) {
            redirect.addFlashAttribute("errorMessage",
                    "This fee agreement has no installment plan to edit.");
            return "redirect:/fees/" + id;
        }
        if (!installmentService.isEditable(id)) {
            redirect.addFlashAttribute("errorMessage",
                    InstallmentService.IMMUTABLE_PLAN_MESSAGE);
            return "redirect:/fees/" + id;
        }
        installmentPlanModel(model, account);
        model.addAttribute("editing", true);
        model.addAttribute("rows", installmentService.scheduleOf(id));
        model.addAttribute("currentTotal", installmentService.scheduledTotal(id));
        return "fees/installment-form";
    }

    /**
     * Replaces the schedule of an existing installment plan. The
     * submitted rows are validated server side and the whole schedule
     * is replaced in one transaction; an invalid submission leaves the
     * original schedule untouched.
     */
    @PostMapping("/{id}/installments/edit")
    public String replaceInstallmentPlan(@PathVariable Long id,
                                         HttpServletRequest request,
                                         Model model,
                                         RedirectAttributes redirect) {
        List<InstallmentInput> rows = readRows(request.getParameterMap());
        try {
            installmentService.replacePlan(id, rows, admin());
            redirect.addFlashAttribute("successMessage",
                    "Installment plan updated. The payments, their receipts and the fee agreement are unchanged.");
            return "redirect:/fees/" + id;
        } catch (AccessDeniedException e) {
            throw e;
        } catch (ImmutablePlanException e) {
            // Nothing the administrator typed can change this: the
            // schedule has become part of the financial record.
            redirect.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/fees/" + id;
        } catch (BusinessRuleException e) {
            FeeAccount account = feeAccountService.findWithDetails(id);
            if (account == null) {
                redirect.addFlashAttribute("errorMessage", "Fee agreement not found.");
                return "redirect:/fees";
            }
            installmentPlanModel(model, account);
            model.addAttribute("editing", true);
            model.addAttribute("rows", rows);
            model.addAttribute("currentTotal", installmentService.scheduledTotal(id));
            model.addAttribute("errorMessage", e.getMessage());
            return "fees/installment-form";
        }
    }

    /** Collection summary for a date range (defaults to the current business month). */
    @GetMapping("/collections")
    public String collections(@RequestParam(required = false) LocalDate from,
                              @RequestParam(required = false) LocalDate to,
                              Model model) {
        LocalDate monthStart = businessClock.currentMonth().atDay(1);
        LocalDate monthEnd = businessClock.currentMonth().atEndOfMonth();
        LocalDate effectiveFrom = from == null ? monthStart : from;
        LocalDate effectiveTo = to == null ? monthEnd : to;
        model.addAttribute("from", effectiveFrom);
        model.addAttribute("to", effectiveTo);
        model.addAttribute("totalReceived", paymentRepository.receivedBetween(effectiveFrom, effectiveTo));
        model.addAttribute("voidedTotal", paymentRepository.voidedBetween(effectiveFrom, effectiveTo));
        model.addAttribute("methodTotals", paymentRepository.methodTotalsBetween(effectiveFrom, effectiveTo));
        return "fees/collections";
    }

    // ----- helpers ----------------------------------------------------------

    /**
     * The model every installment-plan form needs: the fee agreement and
     * how much of it has already been paid.
     */
    private void installmentPlanModel(Model model, FeeAccount account) {
        model.addAttribute("account", account);
        model.addAttribute("received", account.netFee().subtract(feeAccountService.outstandingOf(account)));
        model.addAttribute("outstanding", feeAccountService.outstandingOf(account));
    }

    /**
     * The summary of an installment plan: how many installments, how
     * many of them are overdue and how much of the schedule is still
     * open.
     */
    public record InstallmentSummary(int count,
                                     int overdueCount,
                                     BigDecimal scheduled,
                                     BigDecimal paid,
                                     BigDecimal remaining,
                                     LocalDate nextDueDate) {
    }

    private static InstallmentSummary installmentSummary(List<InstallmentService.InstallmentView> views) {
        if (views.isEmpty()) {
            return new InstallmentSummary(0, 0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null);
        }
        int overdue = 0;
        BigDecimal scheduled = BigDecimal.ZERO;
        BigDecimal paid = BigDecimal.ZERO;
        LocalDate nextDue = null;
        for (InstallmentService.InstallmentView view : views) {
            scheduled = scheduled.add(view.installment().getAmount());
            paid = paid.add(view.paid());
            if (view.status() == InstallmentStatus.OVERDUE) {
                overdue++;
            }
            LocalDate due = view.installment().getDueDate();
            if (view.remaining().signum() > 0 && (nextDue == null || due.isBefore(nextDue))) {
                nextDue = due;
            }
        }
        return new InstallmentSummary(views.size(), overdue, scheduled, paid,
                scheduled.subtract(paid), nextDue);
    }

    /**
     * The two default rows of the plan form: half of the net fee each,
     * so the schedule already adds up and the administrator only has to
     * adjust the dates.
     */
    private List<InstallmentInput> defaultRows(FeeAccount account) {
        BigDecimal netFee = account.netFee();
        BigDecimal first = netFee.divide(new BigDecimal("2"), 2, java.math.RoundingMode.HALF_UP);
        BigDecimal second = netFee.subtract(first);
        LocalDate today = businessClock.today();
        LocalDate firstDue = account.getDueDate() != null ? account.getDueDate() : today;
        LocalDate secondDue = account.getDueDate() != null
                ? account.getDueDate().plusMonths(1)
                : today.plusMonths(1);
        return List.of(
                new InstallmentInput(first.toPlainString(), firstDue.toString(), "1"),
                new InstallmentInput(second.toPlainString(), secondDue.toString(), "2"));
    }

    /**
     * Reads the submitted plan rows. The rows are posted as repeated
     * parameters; nothing is trusted, the service validates every
     * value.
     */
    private static List<InstallmentInput> readRows(Map<String, String[]> params) {
        String[] amounts = params.getOrDefault("amount", new String[0]);
        String[] dueDates = params.getOrDefault("dueDate", new String[0]);
        String[] numbers = params.getOrDefault("installmentNumber", new String[0]);
        int count = Math.max(amounts.length, Math.max(dueDates.length, numbers.length));
        List<InstallmentInput> rows = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            rows.add(new InstallmentInput(valueAt(amounts, i), valueAt(dueDates, i), valueAt(numbers, i)));
        }
        return rows;
    }

    private static String valueAt(String[] values, int index) {
        return index < values.length ? values[index] : null;
    }

    private UserAccount admin() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication != null
                && authentication.getPrincipal() instanceof AppUserPrincipal principal)) {
            throw new AccessDeniedException("Not authenticated.");
        }
        return userAccountRepository.findById(principal.id())
                .orElseThrow(() -> new AccessDeniedException("Your account no longer exists."));
    }

    /**
     * Derived (not stored) display state: UNPAID, PARTIALLY_PAID or
     * PAID; OVERDUE is a separate flag because it depends on the due
     * date in addition to the balance.
     */
    private static String feeStatus(FeeAccount account, BigDecimal outstanding) {
        BigDecimal net = account.netFee();
        if (net.compareTo(BigDecimal.ZERO) <= 0 || outstanding.compareTo(BigDecimal.ZERO) <= 0) {
            return "PAID";
        }
        if (account.netFee().subtract(outstanding).signum() > 0) {
            return "PARTIALLY_PAID";
        }
        return "UNPAID";
    }

    private static String pagerUrl(String base, String q, Long courseId, String academicYear) {
        StringBuilder url = new StringBuilder(base);
        boolean first = true;
        if (q != null && !q.isBlank()) {
            url.append("?q=").append(q);
            first = false;
        }
        if (courseId != null) {
            url.append(first ? "?" : "&").append("courseId=").append(courseId);
            first = false;
        }
        if (academicYear != null && !academicYear.isBlank()) {
            url.append(first ? "?" : "&").append("academicYear=").append(academicYear);
        }
        return url.toString();
    }

    private static FeeAccountForm fromAccount(FeeAccount account) {
        FeeAccountForm form = new FeeAccountForm();
        form.setEnrollmentId(account.getEnrollment().getId());
        form.setGrossFee(account.getGrossFee());
        form.setDiscountAmount(account.getDiscountAmount());
        form.setAcademicYear(account.getAcademicYear());
        form.setDueDate(account.getDueDate());
        form.setNotes(account.getNotes() == null ? "" : account.getNotes());
        return form;
    }
}
