package com.omsai.coaching.batch;

import com.omsai.coaching.common.BusinessRuleException;
import com.omsai.coaching.course.Course;
import com.omsai.coaching.subject.Subject;
import com.omsai.coaching.support.Fixtures;
import com.omsai.coaching.teacher.Teacher;
import com.omsai.coaching.teacher.TeacherService;
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
 * Batch management rules: creation, date validation, unique names,
 * course-scoped subjects and teacher assignments.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class BatchServiceTest {

    @Autowired
    private BatchService batchService;

    @Autowired
    private TeacherService teacherService;

    @Autowired
    private Fixtures fixtures;

    private BatchForm form(Course course) {
        BatchForm form = new BatchForm();
        form.setCourseId(course.getId());
        form.setName("Morning Batch");
        form.setAcademicYear("2026-27");
        form.setStartDate(LocalDate.of(2026, 9, 1));
        form.setEndDate(LocalDate.of(2027, 3, 31));
        return form;
    }

    @Test
    void create_persistsBatch() {
        Course course = fixtures.course("Batch Course");
        BatchForm form = form(course);
        Batch batch = batchService.create(form);

        assertThat(batch.getId()).isNotNull();
        Batch loaded = batchService.get(batch.getId());
        assertThat(loaded.getCourse().getId()).isEqualTo(course.getId());
        assertThat(loaded.getAcademicYear()).isEqualTo("2026-27");
        assertThat(loaded.getStatus()).isEqualTo(Batch.Status.ACTIVE);
    }

    @Test
    void create_rejectsEndDateBeforeStartDate() {
        Course course = fixtures.course("Date Course");
        BatchForm form = form(course);
        form.setEndDate(LocalDate.of(2026, 8, 1));
        assertThatThrownBy(() -> batchService.create(form))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Batch end date cannot be before the start date.");
    }

    @Test
    void create_rejectsDuplicateNameInSameCourseAndYear() {
        Course course = fixtures.course("Dup Course");
        batchService.create(form(course));
        assertThatThrownBy(() -> batchService.create(form(course)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("This course already has a batch named \"Morning Batch\"");
    }

    @Test
    void create_allowsSameNameInAnotherCourseOrYear() {
        Course courseA = fixtures.course("Name Course A");
        Course courseB = fixtures.course("Name Course B");
        batchService.create(form(courseA));
        batchService.create(form(courseB)); // same name, other course

        BatchForm nextYear = form(courseA);
        nextYear.setAcademicYear("2027-28");
        batchService.create(nextYear); // same name+course, other year
    }

    @Test
    void create_rejectsInactiveCourse() {
        Course course = fixtures.course("Inactive Course");
        fixtures.deactivate(course);
        assertThatThrownBy(() -> batchService.create(form(course)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Batches can only be created for an active course.");
    }

    @Test
    void update_changesEditablesButNeverTheCourse() {
        Course course = fixtures.course("Upd Course");
        Batch batch = batchService.create(form(course));
        Course wrongCourse = fixtures.course("Other Course");

        BatchForm form = form(wrongCourse); // attempt to sneak in another course
        form.setName("Renamed Batch");
        Batch updated = batchService.update(batch.getId(), form);

        assertThat(updated.getName()).isEqualTo("Renamed Batch");
        assertThat(updated.getCourse().getId()).isEqualTo(course.getId());
    }

    @Test
    void addSubject_rejectsSubjectFromAnotherCourse() {
        Course courseA = fixtures.course("Subject Course A");
        Course courseB = fixtures.course("Subject Course B");
        Subject subjectB = fixtures.subject("Outer Subject");
        var csB = fixtures.assign(courseB, subjectB);
        Batch batchA = batchService.create(form(courseA));

        assertThatThrownBy(() -> batchService.addSubject(batchA.getId(), csB.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("cannot be assigned to a batch of");

        assertThat(batchService.subjectsOf(batchA)).isEmpty();
    }

    @Test
    void addSubject_thenRejectsDuplicate() {
        Course course = fixtures.course("Dup Subject Course");
        Subject subject = fixtures.subject("Plain Subject");
        var cs = fixtures.assign(course, subject);
        Batch batch = batchService.create(form(course));

        batchService.addSubject(batch.getId(), cs.getId());
        assertThat(batchService.subjectsOf(batch)).hasSize(1);

        assertThatThrownBy(() -> batchService.addSubject(batch.getId(), cs.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("That subject is already assigned to this batch.");

        assertThat(batchService.subjectsOf(batch)).hasSize(1);
    }

    @Test
    void assignTeacher_assignsAndClears() {
        Course course = fixtures.course("Teacher Course");
        Subject subject = fixtures.subject("Assigned Subject");
        var cs = fixtures.assign(course, subject);
        Batch batch = batchService.create(form(course));
        batchService.addSubject(batch.getId(), cs.getId());
        BatchSubject bs = batchService.subjectsOf(batch).get(0);

        Teacher teacher = fixtures.teacher("Assigned Teacher");
        batchService.assignTeacher(bs.getId(), teacher.getId());

        BatchSubject loaded = batchService.subjectsOf(batch).get(0);
        assertThat(loaded.getTeacher()).isNotNull();
        assertThat(loaded.getTeacher().getId()).isEqualTo(teacher.getId());

        batchService.assignTeacher(bs.getId(), null);
        loaded = batchService.subjectsOf(batch).get(0);
        assertThat(loaded.getTeacher()).isNull();
    }

    @Test
    void assignTeacher_rejectsInactiveTeacher() {
        Course course = fixtures.course("Inactive Teacher Course");
        Subject subject = fixtures.subject("Unassigned Subject");
        var cs = fixtures.assign(course, subject);
        Batch batch = batchService.create(form(course));
        batchService.addSubject(batch.getId(), cs.getId());
        BatchSubject bs = batchService.subjectsOf(batch).get(0);

        Teacher teacher = fixtures.teacher("Inactive Teacher");
        teacherService.setStatus(teacher.getId(), false);

        assertThatThrownBy(() -> batchService.assignTeacher(bs.getId(), teacher.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("is inactive and cannot receive new assignments");
    }

    @Test
    void search_listsBatchesOfCourse() {
        Course course = fixtures.course("Search Batch Course");
        batchService.create(form(course));
        var page = batchService.search(null, course.getId(), null, PageRequest.of(0, 10));
        assertThat(page.getContent()).hasSize(1);
    }
}
