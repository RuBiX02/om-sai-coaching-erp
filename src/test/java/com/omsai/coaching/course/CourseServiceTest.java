package com.omsai.coaching.course;

import com.omsai.coaching.common.BusinessRuleException;
import com.omsai.coaching.support.Fixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Course management rules: creation, unique names, editing, status,
 * subject assignment and removal protection.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CourseServiceTest {

    @Autowired
    private CourseService courseService;

    @Autowired
    private Fixtures fixtures;

    private CourseForm form(String name, String category) {
        CourseForm form = new CourseForm();
        form.setName(name);
        form.setCategory(category);
        return form;
    }

    @Test
    void create_persistsCourse() {
        CourseForm form = form("Class XI Science", "Cambridge");
        form.setDurationMonths(24);
        form.setDescription("Foundation course");
        Course course = courseService.create(form);

        assertThat(course.getId()).isNotNull();
        Course loaded = courseService.get(course.getId());
        assertThat(loaded.getName()).isEqualTo("Class XI Science");
        assertThat(loaded.getCategory()).isEqualTo("Cambridge");
        assertThat(loaded.getDurationMonths()).isEqualTo(24);
        assertThat(loaded.getStatus()).isEqualTo(Course.Status.ACTIVE);
    }

    @Test
    void create_rejectsDuplicateName() {
        courseService.create(form("JEE Foundation", "Integrated"));
        assertThatThrownBy(() -> courseService.create(form("JEE foundation", "Integrated")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("A course with the name \"JEE foundation\" already exists");
    }

    @Test
    void update_renamesCourse() {
        Course course = courseService.create(form("Chemistry Crash", "Cambridge"));
        Course updated = courseService.update(course.getId(), form("Chemistry Advanced", "State Board"));
        assertThat(updated.getName()).isEqualTo("Chemistry Advanced");
        assertThat(updated.getCategory()).isEqualTo("State Board");
    }

    @Test
    void update_fails_whenAnotherCourseUsesTheName() {
        Course first = courseService.create(form("Physics Core", "Cambridge"));
        courseService.create(form("Biology Core", "Cambridge"));
        assertThatThrownBy(() -> courseService.update(first.getId(), form("biology core", "Cambridge")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void setStatus_switchesBetweenActiveAndInactive() {
        Course course = courseService.create(form("Maths Basic", "Academic"));

        Course deactivated = courseService.setStatus(course.getId(), false);
        assertThat(deactivated.getStatus()).isEqualTo(Course.Status.INACTIVE);

        Course reactivated = courseService.setStatus(course.getId(), true);
        assertThat(reactivated.getStatus()).isEqualTo(Course.Status.ACTIVE);
    }

    @Test
    void assignSubject_addsAndRejectsDuplicates() {
        Course course = courseService.create(form("Assign Course", "Academic"));
        var subject = fixtures.subject("Simple Subject");
        courseService.assignSubject(course.getId(), subject.getId());

        assertThat(courseService.assignedSubjects(course)).hasSize(1);

        assertThatThrownBy(() -> courseService.assignSubject(course.getId(), subject.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("is already assigned to this course");
    }

    @Test
    void assignSubject_rejectsSubjectsNotExisting() {
        Course course = courseService.create(form("No Subject Course", "Academic"));
        assertThatThrownBy(() -> courseService.assignSubject(course.getId(), 999_999L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Subject not found.");
    }

    @Test
    void removeSubject_deletesUntaughtAssignment() {
        Course course = courseService.create(form("Removal Course", "Academic"));
        var subject = fixtures.subject("Removable Subject");
        courseService.assignSubject(course.getId(), subject.getId());

        var assignment = courseService.assignedSubjects(course).get(0);
        courseService.removeSubject(course.getId(), assignment.getId());

        assertThat(courseService.assignedSubjects(course)).isEmpty();
    }

    @Test
    void removeSubject_blockedWhileSubjectIsTaught() {
        Course course = fixtures.course("Taught Course");
        var subject = fixtures.subject("Taught Subject");
        var cs = fixtures.assign(course, subject);
        var batch = fixtures.batch(course, "Taught Batch");
        fixtures.assignCourseSubject(batch, cs);

        var assignment = courseService.assignedSubjects(course).get(0);
        assertThatThrownBy(() -> courseService.removeSubject(course.getId(), assignment.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("cannot be removed");
        assertThat(courseService.assignedSubjects(course)).hasSize(1);
    }

    @Test
    void search_findsCoursesByName() {
        courseService.create(form("Searchium Alpha", "Academic"));
        var page = courseService.search("searchium", null, PageRequest.of(0, 10));
        assertThat(page.getContent()).isNotEmpty();
    }
}
