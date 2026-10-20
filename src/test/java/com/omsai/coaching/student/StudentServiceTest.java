package com.omsai.coaching.student;

import com.omsai.coaching.common.BusinessRuleException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Student management rules: registration with unique generated codes,
 * editing and active flag.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class StudentServiceTest {

    @Autowired
    private StudentService studentService;

    private StudentForm form(String fullName) {
        StudentForm form = new StudentForm();
        form.setFullName(fullName);
        form.setPhone("9111111111");
        form.setAdmissionDate(LocalDate.of(2026, 8, 15));
        return form;
    }

    @Test
    void create_generatesUniqueStudentCode() {
        Student first = studentService.create(form("Riya Sharma"));
        Student second = studentService.create(form("Aman Patel"));

        assertThat(first.getStudentCode()).matches("OMS-\\d{4}-\\d{5}");
        assertThat(second.getStudentCode()).matches("OMS-\\d{4}-\\d{5}");
        assertThat(first.getStudentCode())
                .isNotEqualTo(second.getStudentCode());

        Student loaded = studentService.get(first.getId());
        assertThat(loaded.getFullName()).isEqualTo("Riya Sharma");
        assertThat(loaded.isActive()).isTrue();
    }

    @Test
    void update_changesProfileFields() {
        StudentForm form = form("Meera Iyer");
        form.setParentName("Suresh Iyer");
        Student created = studentService.create(form);
        assertThat(created.getParentName()).isEqualTo("Suresh Iyer");

        form.setParentName("Lakshmi Iyer");
        form.setPhone("9222222222");
        Student updated = studentService.update(created.getId(), form);

        assertThat(updated.getParentName()).isEqualTo("Lakshmi Iyer");
        assertThat(updated.getPhone()).isEqualTo("9222222222");
    }

    @Test
    void setStatus_switchesActiveFlag() {
        Student student = studentService.create(form("Deactivate Me"));

        Student deactivated = studentService.setStatus(student.getId(), false);
        assertThat(deactivated.isActive()).isFalse();

        Student reactivated = studentService.setStatus(student.getId(), true);
        assertThat(reactivated.isActive()).isTrue();
    }

    @Test
    void search_findsStudentsByNameAndCode() {
        Student student = studentService.create(form("Searchable Student"));
        var page = studentService.search("searchable", null, PageRequest.of(0, 10));
        assertThat(page.getContent())
                .extracting(Student::getStudentCode)
                .contains(student.getStudentCode());
    }

    @Test
    void get_unknownStudentFails() {
        assertThatThrownBy(() -> studentService.get(999_999L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Student not found.");
    }
}
