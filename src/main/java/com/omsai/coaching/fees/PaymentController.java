package com.omsai.coaching.fees;

import com.omsai.coaching.common.BusinessClock;
import com.omsai.coaching.common.BusinessRuleException;
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
 * Payment collection pages: record, search, void and the printable
 * receipt. Administrator only. Balances are computed server-side.
 */
@Controller
@RequestMapping("/payments")
@PreAuthorize("hasRole('ADMIN')")
public class PaymentController {

    private final PaymentService paymentService;
    private final FeeAccountService feeAccountService;
    private final UserAccountRepository userAccountRepository;
    private final BusinessClock businessClock;

    public PaymentController(PaymentService paymentService,
                             FeeAccountService feeAccountService,
                             UserAccountRepository userAccountRepository,
                             BusinessClock businessClock) {
        this.paymentService = paymentService;
        this.feeAccountService = feeAccountService;
        this.userAccountRepository = userAccountRepository;
        this.businessClock = businessClock;
    }

    /** Payment search: receipt number, student, date range, method, status. */
    @GetMapping
    public String list(@RequestParam(required = false) String q,
                       @RequestParam(required = false) LocalDate from,
                       @RequestParam(required = false) LocalDate to,
                       @RequestParam(required = false) String method,
                       @RequestParam(required = false) String status,
                       @RequestParam(defaultValue = "0") int page,
                       Model model) {
        Payment.Method methodFilter = parseMethod(method);
        Payment.Status statusFilter = parseStatus(status);
        var pageable = PageRequest.of(page, 20);
        model.addAttribute("payments",
                paymentService.search(q, from, to, methodFilter, statusFilter, pageable));
        model.addAttribute("methods", List.of(Payment.Method.values()));
        model.addAttribute("statuses", List.of(Payment.Status.values()));
        model.addAttribute("q", q);
        model.addAttribute("from", from);
        model.addAttribute("to", to);
        model.addAttribute("method", method);
        model.addAttribute("status", status);
        model.addAttribute("pagerUrl", pagerUrl(q, from, to, method, status));
        return "payments/list";
    }

    /** Record-a-payment form for one fee agreement. */
    @GetMapping("/new")
    public String newForm(@RequestParam Long feeAccountId, Model model) {
        FeeAccount account;
        try {
            account = feeAccountService.getWithDetails(feeAccountId);
        } catch (BusinessRuleException e) {
            model.addAttribute("errorMessage", e.getMessage());
            return "error/error";
        }
        PaymentForm form = new PaymentForm();
        form.setFeeAccountId(feeAccountId);
        form.setPaymentDate(businessClock.today());
        form.setPaymentMethod(Payment.Method.CASH);
        model.addAttribute("form", form);
        model.addAttribute("account", account);
        model.addAttribute("outstanding", feeAccountService.outstandingOf(account));
        return "payments/form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("form") PaymentForm form,
                         BindingResult binding,
                         Model model,
                         RedirectAttributes redirect) {
        if (binding.hasErrors()) {
            FeeAccount account = null;
            try {
                account = feeAccountService.getWithDetails(form.getFeeAccountId());
            } catch (BusinessRuleException e) {
                model.addAttribute("errorMessage", e.getMessage());
                return "error/error";
            }
            model.addAttribute("account", account);
            model.addAttribute("outstanding", feeAccountService.outstandingOf(account));
            return "payments/form";
        }
        try {
            Payment payment = paymentService.record(form, admin());
            redirect.addFlashAttribute("successMessage",
                    "Payment recorded. Receipt " + payment.getReceiptNumber() + ".");
            return "redirect:/payments/" + payment.getId() + "/receipt";
        } catch (AccessDeniedException e) {
            throw e;
        } catch (BusinessRuleException e) {
            FeeAccount account = feeAccountService.getWithDetails(form.getFeeAccountId());
            model.addAttribute("account", account);
            model.addAttribute("outstanding", feeAccountService.outstandingOf(account));
            model.addAttribute("errorMessage", e.getMessage());
            return "payments/form";
        }
    }

    /** Printable receipt page (browser print to paper or PDF, no PDF dependency). */
    @GetMapping("/{id}/receipt")
    public String receipt(@PathVariable Long id, Model model) {
        Payment payment = paymentService.getWithDetails(id);
        BigDecimal outstanding = feeAccountService.outstandingOf(payment.getFeeAccount());
        model.addAttribute("payment", payment);
        model.addAttribute("outstandingNow", outstanding);
        return "payments/receipt";
    }

    /** Voiding requires a reason; the form lives on the fee-account page. */
    @PostMapping("/{id}/void")
    public String voidPayment(@PathVariable Long id,
                              @Valid @ModelAttribute("form") VoidPaymentForm form,
                              BindingResult binding,
                              RedirectAttributes redirect) {
        Long feeAccountId;
        try {
            Payment payment = paymentService.getWithDetails(id);
            feeAccountId = payment.getFeeAccount().getId();
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/payments";
        }
        if (binding.hasErrors()) {
            redirect.addFlashAttribute("errorMessage", firstFieldError(binding));
            return "redirect:/fees/" + feeAccountId;
        }
        try {
            Payment voided = paymentService.voidPayment(id, form.getReason(), admin());
            redirect.addFlashAttribute("successMessage",
                    "Payment " + voided.getReceiptNumber() + " voided. The outstanding balance has been recalculated.");
        } catch (AccessDeniedException e) {
            throw e;
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/fees/" + feeAccountId;
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

    private static String firstFieldError(BindingResult binding) {
        return binding.getAllErrors().isEmpty()
                ? "Please check the form."
                : binding.getAllErrors().get(0).getDefaultMessage();
    }

    private static Payment.Method parseMethod(String method) {
        if (method == null || method.isBlank()) {
            return null;
        }
        try {
            return Payment.Method.valueOf(method);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static Payment.Status parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return Payment.Status.valueOf(status);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String pagerUrl(String q, LocalDate from, LocalDate to, String method, String status) {
        StringBuilder url = new StringBuilder("/payments");
        boolean first = true;
        if (q != null && !q.isBlank()) {
            url.append("?q=").append(q);
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
        if (method != null && !method.isBlank()) {
            url.append(first ? "?" : "&").append("method=").append(method);
            first = false;
        }
        if (status != null && !status.isBlank()) {
            url.append(first ? "?" : "&").append("status=").append(status);
        }
        return url.toString();
    }
}
