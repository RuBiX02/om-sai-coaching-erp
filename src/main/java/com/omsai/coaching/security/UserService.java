package com.omsai.coaching.security;

import com.omsai.coaching.common.BusinessRuleException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Administration of sign-in accounts (ADMIN and TEACHER users). Teacher
 * profiles and login accounts are related but separate concepts; a
 * login account can be linked to a teacher profile from the teacher
 * management screens.
 */
@Service
public class UserService {

    private final UserAccountRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserAccountRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
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
        UserAccount user = new UserAccount();
        user.setFullName(form.getFullName().trim());
        user.setEmail(form.getEmail().trim());
        user.setRole(UserAccount.Role.valueOf(form.getRole()));
        user.setActive(true);
        user.setPasswordHash(passwordEncoder.encode(form.getPassword()));
        return userRepository.save(user);
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
