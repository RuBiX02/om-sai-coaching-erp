package com.omsai.coaching.subject;

import com.omsai.coaching.batch.CourseSubject;
import com.omsai.coaching.batch.CourseSubjectRepository;
import com.omsai.coaching.common.BusinessRuleException;
import com.omsai.coaching.course.Course;
import com.omsai.coaching.course.CourseForm;
import com.omsai.coaching.course.CourseService;
import com.omsai.coaching.support.Fixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The subject lifecycle: an unused subject is disposable, a subject that
 * is already taught keeps its history and is deactivated instead.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SubjectLifecycleTest {

    @Autowired
    private SubjectService subjectService;

    @Autowired
    private SubjectRepository subjectRepository;

    @Autowired
    private CourseService courseService;

    @Autowired
    private CourseSubjectRepository courseSubjectRepository;

    @Autowired
    private Fixtures fixtures;

    private Course course;

    @BeforeEach
    void buildFixture() {
        course = fixtures.course("Subject Lifecycle Course");
    }

    private Subject newSubject(String name) {
        SubjectForm form = new SubjectForm();
        form.setName(name + " " + System.nanoTime());
        return subjectService.create(form);
    }

    // ----- Case A: an unused subject ----------------------------------------

    @Test
    void anUnusedSubjectCanBeDeleted() {
        Subject subject = newSubject("Unused Subject");

        subjectService.delete(subject.getId());

        assertThat(subjectRepository.findById(subject.getId())).isEmpty();
    }

    @Test
    void deletingAnUnknownSubjectIsRefused() {
        assertThatThrownBy(() -> subjectService.delete(999999L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Subject not found");
    }

    @Test
    void deletingTheSameSubjectTwiceDoesNotSucceedTwice() {
        Subject subject = newSubject("Twice Deleted Subject");

        subjectService.delete(subject.getId());

        assertThatThrownBy(() -> subjectService.delete(subject.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Subject not found");
    }

    // ----- Case B: a subject that is already used ---------------------------

    @Test
    void aSubjectUsedByACourseCannotBeDeleted() {
        Subject subject = newSubject("Taught Subject");
        fixtures.assign(course, subject);

        assertThatThrownBy(() -> subjectService.delete(subject.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("cannot be deleted without losing the teaching history")
                .hasMessageContaining("Deactivate it instead");
    }

    @Test
    void aRefusedDeletionLeavesTheSubjectAndItsCourseRelationshipIntact() {
        Subject subject = newSubject("Kept Subject");
        CourseSubject link = fixtures.assign(course, subject);

        assertThatThrownBy(() -> subjectService.delete(subject.getId()))
                .isInstanceOf(BusinessRuleException.class);

        assertThat(subjectRepository.findById(subject.getId())).isPresent();
        assertThat(courseSubjectRepository.findById(link.getId())).isPresent();
        assertThat(subjectService.coursesOf(subject)).hasSize(1);
    }

    @Test
    void aReferencedSubjectCanBeDeactivated() {
        Subject subject = newSubject("Deactivated Subject");
        fixtures.assign(course, subject);

        Subject deactivated = subjectService.setActive(subject.getId(), false);

        assertThat(deactivated.isActive()).isFalse();
        assertThat(subjectRepository.findById(subject.getId()).orElseThrow().isActive()).isFalse();
    }

    @Test
    void deactivatingKeepsTheCourseRelationshipAndItsHistory() {
        Subject subject = newSubject("Historic Subject");
        CourseSubject link = fixtures.assign(course, subject);

        subjectService.setActive(subject.getId(), false);

        // The relationship is untouched and still readable.
        assertThat(courseSubjectRepository.findById(link.getId())).isPresent();
        List<CourseSubject> courses = subjectService.coursesOf(subject);
        assertThat(courses).hasSize(1);
        assertThat(courses.get(0).getCourse().getId()).isEqualTo(course.getId());
        assertThat(courses.get(0).getSubject().getId()).isEqualTo(subject.getId());
    }

    @Test
    void anInactiveSubjectIsNotOfferedForANewCourseAssignment() {
        Subject subject = newSubject("Not Offered Subject");
        fixtures.assign(course, subject);
        subjectService.setActive(subject.getId(), false);

        Course other = fixtures.course("Another Course");

        assertThat(courseService.assignableSubjects(other))
                .noneMatch(s -> s.getId().equals(subject.getId()));
    }

    @Test
    void anActiveSubjectIsStillOfferedForANewCourseAssignment() {
        Subject subject = newSubject("Offered Subject");

        Course other = fixtures.course("Another Course");

        assertThat(courseService.assignableSubjects(other))
                .anyMatch(s -> s.getId().equals(subject.getId()));
    }

    @Test
    void anInactiveSubjectCannotBeAssignedToACourse() {
        Subject subject = newSubject("Rejected Subject");
        subjectService.setActive(subject.getId(), false);
        Course other = fixtures.course("Another Course");

        assertThatThrownBy(() -> courseService.assignSubject(other.getId(), subject.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("deactivated and cannot be assigned");
    }

    @Test
    void anInactiveSubjectKeepsItsExistingCourseAssignment() {
        Subject subject = newSubject("Still Assigned Subject");
        fixtures.assign(course, subject);

        subjectService.setActive(subject.getId(), false);

        // The course still lists it and the relationship is still there.
        assertThat(courseService.assignedSubjects(course))
                .anyMatch(cs -> cs.getSubject().getId().equals(subject.getId()));
        assertThat(courseSubjectRepository.countForSubject(subject.getId())).isEqualTo(1);
    }

    // ----- Case C: reactivation ----------------------------------------------

    @Test
    void aDeactivatedSubjectCanBeReactivated() {
        Subject subject = newSubject("Reactivated Subject");
        subjectService.setActive(subject.getId(), false);

        Subject reactivated = subjectService.setActive(subject.getId(), true);

        assertThat(reactivated.isActive()).isTrue();
    }

    @Test
    void reactivatingDoesNotRecreateAnyRelationship() {
        Subject subject = newSubject("Single Link Subject");
        fixtures.assign(course, subject);
        subjectService.setActive(subject.getId(), false);

        subjectService.setActive(subject.getId(), true);

        assertThat(courseSubjectRepository.countForSubject(subject.getId())).isEqualTo(1);
        assertThat(subjectService.coursesOf(subject)).hasSize(1);
    }

    @Test
    void aReactivatedSubjectIsOfferedAgain() {
        Subject subject = newSubject("Offered Again Subject");
        subjectService.setActive(subject.getId(), false);
        Course other = fixtures.course("Another Course");

        subjectService.setActive(subject.getId(), true);

        assertThat(courseService.assignableSubjects(other))
                .anyMatch(s -> s.getId().equals(subject.getId()));
    }

    // ----- invalid transitions -----------------------------------------------

    @Test
    void deactivatingAnAlreadyDeactivatedSubjectIsRefused() {
        Subject subject = newSubject("Twice Deactivated Subject");
        subjectService.setActive(subject.getId(), false);

        assertThatThrownBy(() -> subjectService.setActive(subject.getId(), false))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("already deactivated");
    }

    @Test
    void activatingAnAlreadyActiveSubjectIsRefused() {
        Subject subject = newSubject("Twice Activated Subject");

        assertThatThrownBy(() -> subjectService.setActive(subject.getId(), true))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("already active");
    }

    @Test
    void deactivatingAnUnknownSubjectIsRefused() {
        assertThatThrownBy(() -> subjectService.setActive(999999L, false))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Subject not found");
    }

    @Test
    void anUnusedSubjectCanStillBeDeactivatedAndActivatedAgain() {
        Subject subject = newSubject("Toggle Unused Subject");

        subjectService.setActive(subject.getId(), false);
        assertThat(subjectService.isActive(subject.getId())).isFalse();
        subjectService.setActive(subject.getId(), true);
        assertThat(subjectService.isActive(subject.getId())).isTrue();
    }

    // ----- regression: the existing course management ------------------------

    @Test
    void anExistingSubjectCanStillBeAssignedToACourse() {
        Subject subject = newSubject("Assignable Subject");

        courseService.assignSubject(course.getId(), subject.getId());

        assertThat(courseService.assignedSubjects(course))
                .anyMatch(cs -> cs.getSubject().getId().equals(subject.getId()));
    }

    @Test
    void theSameSubjectCannotBeAssignedTwiceToTheSameCourse() {
        Subject subject = newSubject("Duplicate Subject");
        courseService.assignSubject(course.getId(), subject.getId());

        assertThatThrownBy(() -> courseService.assignSubject(course.getId(), subject.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("already assigned");
    }

    @Test
    void aSubjectCanStillBeRenamedAndDescribed() {
        Subject subject = newSubject("Rename Me");
        SubjectForm form = new SubjectForm();
        form.setName("Renamed Subject");
        form.setDescription("A description.");

        Subject updated = subjectService.update(subject.getId(), form);

        assertThat(updated.getName()).isEqualTo("Renamed Subject");
        assertThat(updated.getDescription()).isEqualTo("A description.");
        assertThat(updated.isActive()).isTrue();
    }

    @Test
    void aSubjectNameIsStillUnique() {
        Subject subject = newSubject("Unique Name Subject");
        SubjectForm form = new SubjectForm();
        form.setName(subject.getName());

        assertThatThrownBy(() -> subjectService.create(form))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void aCourseCanStillBeCreatedAndAssignedSubjects() {
        CourseForm form = new CourseForm();
        form.setName("Regression Course " + System.nanoTime());
        form.setCategory("School Tuition");
        form.setDurationMonths(12);
        Course created = courseService.create(form);
        Subject subject = newSubject("Regression Subject");

        courseService.assignSubject(created.getId(), subject.getId());

        assertThat(created.isActive()).isTrue();
        assertThat(courseService.assignedSubjects(created)).hasSize(1);
    }
}
