package com.omsai.coaching.attendance;

import com.omsai.coaching.batch.BatchSubject;
import com.omsai.coaching.security.AppUserPrincipal;
import com.omsai.coaching.security.UserAccount;
import com.omsai.coaching.security.UserAccountRepository;
import com.omsai.coaching.teacher.Teacher;
import com.omsai.coaching.teacher.TeacherRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Resolves the signed-in user's rights over class-session operations.
 * ADMIN may create and manage sessions everywhere; TEACHER may only
 * create and manage sessions for batch subjects currently assigned to
 * their linked, active teacher profile. Authorization is enforced here
 * in the service layer - hiding buttons in the UI is not a control.
 */
@Component
public class SessionAuthority {

    private final UserAccountRepository userAccountRepository;
    private final TeacherRepository teacherRepository;

    public SessionAuthority(UserAccountRepository userAccountRepository,
                            TeacherRepository teacherRepository) {
        this.userAccountRepository = userAccountRepository;
        this.teacherRepository = teacherRepository;
    }

    /**
     * The current user as an entity (for createdBy / markedBy / receivedBy
     * references and role checks). Uses a real findById instead of a lazy
     * reference because some callers (page controllers outside any
     * transaction) only read simple columns such as the role.
     */
    public UserAccount currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !(authentication.getPrincipal() instanceof AppUserPrincipal principal)) {
            throw new AccessDeniedException("Not authenticated.");
        }
        return userAccountRepository.findById(principal.id())
                .orElseThrow(() -> new AccessDeniedException("Your account no longer exists."));
    }

    /** The linked teacher profile of the current user; null when the user is an administrator or has no active profile. */
    public Teacher currentTeacher(UserAccount user) {
        return teacherRepository.findByLinkedUserId(user.getId()).orElse(null);
    }

    public boolean isTeacher(UserAccount user) {
        return user.getRole() == UserAccount.Role.TEACHER;
    }

    public boolean isAdmin(UserAccount user) {
        return user.getRole() == UserAccount.Role.ADMIN;
    }

    /** True when the user may create or manage sessions for the given batch-subject assignment. */
    public boolean canManage(BatchSubject batchSubject, UserAccount user) {
        if (isAdmin(user)) {
            return true;
        }
        Teacher teacher = currentTeacher(user);
        return teacher != null
                && teacher.isActive()
                && batchSubject.getTeacher() != null
                && batchSubject.getTeacher().getId().equals(teacher.getId());
    }

    /** Throws Spring Security's AccessDeniedException (rendered as the 403 page) when the user may not manage. */
    public void requireCanManage(BatchSubject batchSubject, UserAccount user) {
        if (!canManage(batchSubject, user)) {
            throw new AccessDeniedException(
                    "You may only create or manage sessions for subjects assigned to you.");
        }
    }

    /** Throws AccessDeniedException when a teacher requests a session they do not own. */
    public void requireCanView(ClassSession session, UserAccount user) {
        requireCanManage(session.getBatchSubject(), user);
    }
}
