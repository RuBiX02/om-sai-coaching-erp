package com.omsai.coaching.teacher;

import com.omsai.coaching.common.BusinessRuleException;
import com.omsai.coaching.security.UserAccount;
import com.omsai.coaching.support.Fixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Teacher profile rules, including linking login accounts.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TeacherServiceTest {

    @Autowired
    private TeacherService teacherService;

    @Autowired
    private Fixtures fixtures;

    private TeacherForm form(String fullName) {
        TeacherForm form = new TeacherForm();
        form.setFullName(fullName);
        form.setPhone("7111111111");
        return form;
    }

    @Test
    void create_persistsProfileWithoutAccount() {
        TeacherForm form = form("Standalone Teacher");
        form.setEmail("standalone@omsai.local");
        Teacher teacher = teacherService.create(form);

        assertThat(teacher.getId()).isNotNull();
        Teacher loaded = teacherService.get(teacher.getId());
        assertThat(loaded.getLinkedUser()).isNull();
        assertThat(loaded.isActive()).isTrue();
    }

    @Test
    void create_linksTeacherRoleAccount() {
        UserAccount account = fixtures.teacherUser("link.ok");
        TeacherForm form = form("Linked Teacher");
        form.setUserId(account.getId());
        Teacher teacher = teacherService.create(form);

        Teacher loaded = teacherService.get(teacher.getId());
        assertThat(loaded.getLinkedUser()).isNotNull();
        assertThat(loaded.getLinkedUser().getEmail()).isEqualTo(account.getEmail());

        assertThat(teacherService.findByUser(account.getId())).isNotNull();
    }

    @Test
    void create_rejectsAdminRoleAccount() {
        UserAccount admin = fixtures.adminUser("link.admin");
        TeacherForm form = form("Wrong Role");
        form.setUserId(admin.getId());

        assertThatThrownBy(() -> teacherService.create(form))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Only accounts with the TEACHER role can be linked to a teacher profile.");
    }

    @Test
    void create_rejectsAccountLinkedToAnotherProfile() {
        UserAccount account = fixtures.teacherUser("link.taken");
        TeacherForm first = form("First Owner");
        first.setUserId(account.getId());
        teacherService.create(first);

        TeacherForm second = form("Second Owner");
        second.setUserId(account.getId());

        assertThatThrownBy(() -> teacherService.create(second))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("That account is already linked to another teacher profile")
                .hasMessageContaining("First Owner");
    }

    @Test
    void create_rejectsUnknownAccount() {
        TeacherForm form = form("Ghost Link");
        form.setUserId(999_999L);
        assertThatThrownBy(() -> teacherService.create(form))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Selected account does not exist.");
    }

    @Test
    void update_updatesFieldsAndKeepsLink() {
        UserAccount account = fixtures.teacherUser("link.keep");
        Teacher teacher = fixtures.teacher("Before Update");

        TeacherForm form = form("After Update");
        form.setUserId(account.getId());
        teacherService.update(teacher.getId(), form);

        Teacher loaded = teacherService.get(teacher.getId());
        assertThat(loaded.getFullName()).isEqualTo("After Update");
        assertThat(loaded.getLinkedUser().getEmail()).isEqualTo(account.getEmail());
    }

    @Test
    void setStatus_switchesActiveFlag() {
        Teacher teacher = fixtures.teacher("Toggle Teacher");

        Teacher deactivated = teacherService.setStatus(teacher.getId(), false);
        assertThat(deactivated.isActive()).isFalse();

        Teacher reactivated = teacherService.setStatus(teacher.getId(), true);
        assertThat(reactivated.isActive()).isTrue();
    }
}
