package com.omsai.coaching.security;

import com.omsai.coaching.common.BusinessRuleException;
import com.omsai.coaching.student.Student;
import com.omsai.coaching.student.StudentRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Administration of sign-in accounts (ADMIN, TEACHER and STUDENT users).
 * Teacher profiles and login accounts are related but separate
 * concepts; a login account can be linked to a teacher profile from the
 * teacher management screens.
 *
 * <p>A STUDENT account belongs to exactly one student profile: the
 * database keeps the link one-to-one (unique index on
 * students.user_account_id) and the service refuses a second link with
 * a readable message.</p>
 */
@Service
public class UserService {

    private final UserAccountRepository userRepository;
    private final StudentRepository studentRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserAccountRepository userRepository,
                       StudentRepository studentRepository,
                       PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.studentRepository = studentRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public Page<UserAccount> search(String query, Pageable pageable) {
        String q = (query == null || query.isBlank()) ? "" : query.trim().toLowerCase();
        return userRepository.search(q, pageable);
    }

    @Transactional(readOnly = true)
    public Page<UserAccount> unlinkedTeacherAccounts(Pageable pageable) {
        return userRepository.findUnlinkedTeachers(UserAccount.Role.TEACHER, pageable);
    }

    @Transactional(readOnly = true)
    public boolean existsAdmin() {
        return userRepository.existsByRole(UserAccount.Role.ADMIN);
    }

    @Transactional
    public UserAccount create(UserForm form) {
        if (userRepository.existsByEmailIgnoreCase(form.getEmail().trim())) {
            throw new BusinessRuleException("An account with that email address already exists.");
        }
        UserAccount.Role role = UserAccount.Role.valueOf(form.getRole());
        Student student = role == UserAccount.Role.STUDENT ? studentOf(form.getStudentId()) : null;
        UserAccount user = new UserAccount();
        user.setFullName(form.getFullName().trim());
        user.setEmail(form.getEmail().trim());
        user.setRole(role);
        user.setActive(true);
        user.setPasswordHash(passwordEncoder.encode(form.getPassword()));
        UserAccount saved = userRepository.save(user);
        if (student != null) {
            student.setUserAccount(saved);
            studentRepository.save(student);
        }
        return saved;
    }

    /**
     * Links an existing student account to a student profile. Both
     * sides must be free: one student belongs to one account and one
     * account belongs to one student.
     */
    @Transactional
    public UserAccount linkStudent(Long accountId, Long studentId) {
        UserAccount account = userRepository.findById(accountId)
                .orElseThrow(() -> new BusinessRuleException("Account not found."));
        Student student = studentOf(studentId);
        if (student.getUserAccount() != null) {
            throw new BusinessRuleException(student.getFullName() + " already has a portal account.");
        }
        if (studentRepository.existsByUserAccountId(accountId)) {
            throw new BusinessRuleException("This account is already linked to a student.");
        }
        student.setUserAccount(account);
        studentRepository.save(student);
        return account;
    }

    /** Removes the portal link of an account (the student profile stays). */
    @Transactional
    public void unlinkStudent(Long accountId) {
        UserAccount account = userRepository.findById(accountId)
                .orElseThrow(() -> new BusinessRuleException("Account not found."));
        studentRepository.findByUserAccountId(accountId).ifPresent(student -> {
            student.setUserAccount(null);
            studentRepository.save(student);
        });
    }

    /** The students without a portal account (for the account creation). */
    @Transactional(readOnly = true)
    public java.util.List<Student> studentsWithoutAccount() {
        return studentRepository.findWithoutAccount();
    }

    private Student studentOf(Long studentId) {
        if (studentId == null) {
            throw new BusinessRuleException("Choose the student this account belongs to.");
        }
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new BusinessRuleException("Student not found."));
        if (student.getUserAccount() != null) {
            throw new BusinessRuleException(student.getFullName() + " already has a portal account.");
        }
        return student;
    }

    @Transactional
    public void setActive(Long userId, boolean active) {
        UserAccount user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessRuleException("Account not found."));
        if (user.getRole() == UserAccount.Role.ADMIN && !active && userRepository.countByRoleAndActiveTrue(UserAccount.Role.ADMIN) <= 1) {
            throw new BusinessRuleException("Cannot deactivate the last active administrator account.");
        }
        user.setActive(active);
    }

    @Transactional
    public void resetPassword(Long userId, String newPassword) {
        UserAccount user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessRuleException("Account not found."));
        if (!StringUtils.hasText(newPassword) || newPassword.length() < 12) {
            throw new BusinessRuleException("New password must be at least 12 characters.");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
    }
}
