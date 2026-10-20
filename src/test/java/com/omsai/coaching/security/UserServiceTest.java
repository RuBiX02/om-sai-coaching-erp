package com.omsai.coaching.security;

import com.omsai.coaching.common.BusinessRuleException;
import com.omsai.coaching.security.UserAccountRepository;
import com.omsai.coaching.support.Fixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Sign-in account management rules: unique emails, hashed passwords,
 * last-admin protection and password reset.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UserServiceTest {

    @Autowired
    private UserService userService;

    @Autowired
    private Fixtures fixtures;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private UserAccountRepository userRepository;

    private UserForm form(String email, UserAccount.Role role, String password) {
        UserForm form = new UserForm();
        form.setFullName("Account " + email);
        form.setEmail(email);
        form.setRole(role.name());
        form.setPassword(password);
        return form;
    }

    @Test
    void create_hashesPassword() {
        UserForm form = form("new.admin@omsai.local", UserAccount.Role.ADMIN, "LongEnough#Pass1");
        UserAccount created = userService.create(form);

        assertThat(created.getId()).isNotNull();
        assertThat(created.isActive()).isTrue();
        assertThat(created.getPasswordHash()).isNotEqualTo("LongEnough#Pass1");
        assertThat(passwordEncoder.matches("LongEnough#Pass1", created.getPasswordHash())).isTrue();
    }

    @Test
    void create_rejectsDuplicateEmail() {
        userService.create(form("dup.admin@omsai.local", UserAccount.Role.ADMIN, "LongEnough#Pass1"));
        assertThatThrownBy(() ->
                userService.create(form("DUP.admin@omsai.local", UserAccount.Role.TEACHER, "LongEnough#Pass1")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("An account with that email address already exists.");
    }

    @Test
    void setActive_cannotDeactivateLastAdmin() {
        UserAccount onlyAdmin = userService.create(
                form("last.admin@omsai.local", UserAccount.Role.ADMIN, "LongEnough#Pass2"));

        assertThatThrownBy(() -> userService.setActive(onlyAdmin.getId(), false))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Cannot deactivate the last active administrator account.");
    }

    @Test
    void setActive_allowsDeactivatingWhenAnotherAdminStaysActive() {
        userService.create(form("admin.one@omsai.local", UserAccount.Role.ADMIN, "LongEnough#Pass3"));
        UserAccount adminTwo = userService.create(
                form("admin.two@omsai.local", UserAccount.Role.ADMIN, "LongEnough#Pass3"));

        userService.setActive(adminTwo.getId(), false);

        // one ACTIVE admin left, so success; but now deactivating the other is blocked
        UserAccount adminOne = userRepository_byEmail("admin.one@omsai.local");
        assertThatThrownBy(() -> userService.setActive(adminOne.getId(), false))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Cannot deactivate the last active administrator account.");
    }

    @Test
    void setActive_togglesTeacherAccounts() {
        UserAccount teacher = userService.create(
                form("toggle.teacher@omsai.local", UserAccount.Role.TEACHER, "LongEnough#Pass4"));
        userService.setActive(teacher.getId(), false);

        UserAccount loaded = userRepository_byEmail("toggle.teacher@omsai.local");
        assertThat(loaded.isActive()).isFalse();
    }

    @Test
    void resetPassword_updatesHash() {
        UserAccount account = userService.create(
                form("reset.me@omsai.local", UserAccount.Role.TEACHER, fixtures.safePassword()));
        String oldHash = account.getPasswordHash();

        userService.resetPassword(account.getId(), "Freshly#Reset#2026");

        UserAccount reloaded = userRepository_byEmail("reset.me@omsai.local");
        assertThat(reloaded.getPasswordHash()).isNotEqualTo(oldHash);
        assertThat(passwordEncoder.matches("Freshly#Reset#2026", reloaded.getPasswordHash())).isTrue();
    }

    @Test
    void resetPassword_rejectsShortPasswords() {
        UserAccount account = userService.create(
                form("short.pw@omsai.local", UserAccount.Role.TEACHER, fixtures.safePassword()));

        assertThatThrownBy(() -> userService.resetPassword(account.getId(), "short11char"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("New password must be at least 12 characters.");
    }

    @Test
    void existsAdmin_falseWhenNoAdministratorExists() {
        // Tests roll back their data, so on a fresh test database there is
        // no administrator account at all.
        assertThat(userService.existsAdmin()).isFalse();
    }

    private UserAccount userRepository_byEmail(String email) {
        return userRepository.findByEmailIgnoreCase(email).orElseThrow();
    }
}
