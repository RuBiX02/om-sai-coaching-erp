package com.omsai.coaching.notice;

import com.omsai.coaching.batch.BatchRepository;
import com.omsai.coaching.common.BusinessRuleException;
import com.omsai.coaching.security.AppUserPrincipal;
import com.omsai.coaching.security.UserAccount;
import com.omsai.coaching.security.UserAccountRepository;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * The office notice board: publish a notice to every student or to one
 * batch, and withdraw one that no longer applies.
 */
@Controller
@RequestMapping("/notices")
@PreAuthorize("hasRole('ADMIN')")
public class NoticeController {

    /** The fields of the publish form. */
    public static class NoticeForm {

        @NotBlank
        @Size(max = 160)
        private String title = "";

        @NotBlank
        @Size(max = 5000)
        private String body = "";

        /** "ALL" or "BATCH" as the radio button submits it. */
        private String audience = "ALL";

        private Long batchId;

        public String getTitle() {
            return title;
        }

        public String getBody() {
            return body;
        }

        public String getAudience() {
            return audience;
        }

        public Long getBatchId() {
            return batchId;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public void setBody(String body) {
            this.body = body;
        }

        public void setAudience(String audience) {
            this.audience = audience;
        }

        public void setBatchId(Long batchId) {
            this.batchId = batchId;
        }
    }

    private final NoticeService noticeService;
    private final BatchRepository batchRepository;
    private final UserAccountRepository userAccountRepository;

    public NoticeController(NoticeService noticeService,
                            BatchRepository batchRepository,
                            UserAccountRepository userAccountRepository) {
        this.noticeService = noticeService;
        this.batchRepository = batchRepository;
        this.userAccountRepository = userAccountRepository;
    }

    /** Every notice ever published, newest first. */
    @GetMapping
    public String list(Model model) {
        model.addAttribute("notices", noticeService.forOffice());
        return "notices/list";
    }

    /** The publish form. */
    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("form", new NoticeForm());
        model.addAttribute("batches", batchRepository.findAllForSelection());
        return "notices/form";
    }

    /** Publish a notice as the signed-in administrator. */
    @PostMapping
    public String publish(@ModelAttribute("form") NoticeForm form,
                          BindingResult binding,
                          Model model,
                          RedirectAttributes redirect) {
        if (binding.hasErrors()) {
            model.addAttribute("batches", batchRepository.findAllForSelection());
            return "notices/form";
        }
        try {
            Announcement notice = noticeService.publish(form.getTitle(), form.getBody(),
                    form.getAudience(), form.getBatchId(), admin());
            redirect.addFlashAttribute("successMessage",
                    "Notice published to "
                            + (notice.getBatch() == null
                                    ? "every student"
                                    : "batch " + notice.getBatch().getName()) + ".");
            return "redirect:/notices";
        } catch (BusinessRuleException e) {
            model.addAttribute("errorMessage", e.getMessage());
            model.addAttribute("batches", batchRepository.findAllForSelection());
            return "notices/form";
        }
    }

    /** Stop showing a notice to the students (the record stays). */
    @PostMapping("/{id}/withdraw")
    public String withdraw(@PathVariable Long id, RedirectAttributes redirect) {
        try {
            noticeService.withdraw(id);
            redirect.addFlashAttribute("successMessage",
                    "The notice no longer shows on the student portal.");
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/notices";
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
}
