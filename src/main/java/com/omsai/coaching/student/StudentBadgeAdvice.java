package com.omsai.coaching.student;

import com.omsai.coaching.notice.NoticeService;
import com.omsai.coaching.security.AppUserPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Adds the notice-board badge to every student page: the honest count
 * of the notices this student has not read yet. Other roles and
 * unlinked accounts carry no badge at all, and a failure here must
 * never break a page - the badge is decoration, the data is not.
 */
@ControllerAdvice
public class StudentBadgeAdvice {

    private final StudentPortalService portalService;
    private final NoticeService noticeService;
    private final com.omsai.coaching.security.UserAccountRepository userAccountRepository;

    public StudentBadgeAdvice(StudentPortalService portalService,
                              NoticeService noticeService,
                              com.omsai.coaching.security.UserAccountRepository userAccountRepository) {
        this.portalService = portalService;
        this.noticeService = noticeService;
        this.userAccountRepository = userAccountRepository;
    }

    @ModelAttribute
    public void addNoticeBadge(Model model) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication != null
                && authentication.getPrincipal() instanceof AppUserPrincipal principal)
                || principal.role() != com.omsai.coaching.security.UserAccount.Role.STUDENT) {
            return;
        }
        try {
            var account = userAccountRepository.findById(principal.id()).orElse(null);
            if (account == null) {
                return;
            }
            var student = portalService.studentOf(account);
            int unread = noticeService.unreadCount(portalService.activeBatchIds(student),
                    account.getId());
            model.addAttribute("noticeUnread", unread);
        } catch (RuntimeException ignored) {
            // An account without a student profile simply shows no badge.
        }
    }
}
