package com.omsai.coaching.fees;

import com.omsai.coaching.common.BusinessClock;
import com.omsai.coaching.common.BusinessRuleException;
import com.omsai.coaching.course.CourseRepository;
import com.omsai.coaching.enrollment.EnrollmentRepository;
import com.omsai.coaching.security.AppUserPrincipal;
import com.omsai.coaching.security.UserAccount;
import com.omsai.coaching.security.UserAccountRepository;
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
import java.util.List;

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
    private final EnrollmentRepository enrollmentRepository;
    private final CourseRepository courseRepository;
    private final UserAccountRepository userAccountRepository;
    private final PaymentRepository paymentRepository;
    private final BusinessClock businessClock;

    public FeeAccountController(FeeAccountService feeAccountService,
                                PaymentService paymentService,
                                EnrollmentRepository enrollmentRepository,
                                CourseRepository courseRepository,
                                UserAccountRepository userAccountRepository,
                                PaymentRepository paymentRepository,
                                BusinessClock businessClock) {
        this.feeAccountService = feeAccountService;
        this.paymentService = paymentService;
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
        model.addAttribute("account", account);
        model.addAttribute("received", account.netFee().subtract(outstanding));
        model.addAttribute("outstanding", outstanding);
        model.addAttribute("payments", paymentRepository.findForAccount(id));
        model.addAttribute("feeStatus", feeStatus(account, outstanding));
        model.addAttribute("overdue",
                account.getDueDate() != null
                        && account.getDueDate().isBefore(businessClock.today())
                        && outstanding.compareTo(BigDecimal.ZERO) > 0);
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
