package com.omsai.coaching.enrollment;

import com.omsai.coaching.batch.CourseSubject;
import com.omsai.coaching.course.Course;
import com.omsai.coaching.security.UserAccount;
import com.omsai.coaching.student.Student;
import com.omsai.coaching.subject.Subject;
import com.omsai.coaching.support.Fixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.omsai.coaching.batch.BatchRepository;
import com.omsai.coaching.batch.BatchSubjectRepository;
import com.omsai.coaching.batch.CourseSubjectRepository;
import com.omsai.coaching.batch.Batch;
import com.omsai.coaching.batch.BatchSubject;
import com.omsai.coaching.security.UserAccountRepository;
import com.omsai.coaching.student.StudentRepository;
import com.omsai.coaching.subject.SubjectRepository;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies the database-level guards in the initial migration: partial
 * unique indexes and unique constraints must reject the invalid data even
 * if application code is bypassed.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class EnrollmentConstraintTest {

    @Autowired
    private EnrollmentRepository enrollmentRepository;
    @Autowired
    private StudentRepository studentRepository;
    @Autowired
    private CourseSubjectRepository courseSubjectRepository;
    @Autowired
    private BatchSubjectRepository batchSubjectRepository;
    @Autowired
    private UserAccountRepository userAccountRepository;
    @Autowired
    private SubjectRepository subjectRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private Fixtures fixtures;

    @Test
    void rejectsTwoActiveEnrollmentsInSameBatch() {
        Course course = fixtures.course("Constraint Course");
        Batch batch = fixtures.batch(course, "Constraint Batch");
        Student student = fixtures.student("Constraint Student");

        fixtures.activeEnrollment(student, batch);

        Enrollment duplicate = new Enrollment();
        duplicate.setStudent(student);
        duplicate.setBatch(batch);
        duplicate.setCourse(course);
        duplicate.setEnrollmentDate(java.time.LocalDate.of(2026, 9, 3));
        duplicate.setStatus(Enrollment.Status.ACTIVE);

        assertThatThrownBy(() -> enrollmentRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsTwoActiveEnrollmentsInSameCourseEvenInDifferentBatches() {
        Course course = fixtures.course("Constraint Course C");
        Batch batchA = fixtures.batch(course, "Constraint A");
        Batch batchB = fixtures.batch(course, "Constraint B");
        Student student = fixtures.student("Constraint Course Student");

        fixtures.activeEnrollment(student, batchA);

        Enrollment duplicate = new Enrollment();
        duplicate.setStudent(student);
        duplicate.setBatch(batchB); // different batch, same course
        duplicate.setCourse(course);
        duplicate.setEnrollmentDate(java.time.LocalDate.of(2026, 9, 3));
        duplicate.setStatus(Enrollment.Status.ACTIVE);

        assertThatThrownBy(() -> enrollmentRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsDuplicateCourseSubjectPair() {
        Course course = fixtures.course("Constraint Pair Course");
        Subject subject = fixtures.subject("Constraint Subject");
        CourseSubject first = fixtures.assign(course, subject);

        CourseSubject duplicate = new CourseSubject();
        duplicate.setCourse(course);
        duplicate.setSubject(subject);

        assertThatThrownBy(() -> courseSubjectRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsDuplicateStudentCodeIgnoringCase() {
        Student student = fixtures.student("Code Student");

        Student duplicate = new Student();
        duplicate.setStudentCode(student.getStudentCode().toLowerCase());
        duplicate.setFullName("Code Double");
        duplicate.setPhone("9999999999");
        duplicate.setAdmissionDate(java.time.LocalDate.of(2026, 9, 1));
        duplicate.setActive(true);

        assertThatThrownBy(() -> studentRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsDuplicateUserEmailIgnoringCase() {
        String email = "constraint." + System.nanoTime() + "@omsai.local";
        user(email);
        assertThatThrownBy(() -> user(email.toUpperCase()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsBatchSubjectDuplicateInSameBatch() {
        Course course = fixtures.course("Constraint BS Course");
        Subject subject = fixtures.subject("Constraint BS Subject");
        CourseSubject cs = fixtures.assign(course, subject);
        Batch batch = fixtures.batch(course, "Constraint BS Batch");

        fixtures.assignCourseSubject(batch, cs);

        BatchSubject duplicate = new BatchSubject();
        duplicate.setBatch(batch);
        duplicate.setCourseSubject(cs);

        assertThatThrownBy(() -> batchSubjectRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private void user(String email) {
        UserAccount user = new UserAccount();
        user.setFullName("Duplicate Email");
        user.setEmail(email);
        user.setRole(UserAccount.Role.TEACHER);
        user.setActive(true);
        user.setPasswordHash(passwordEncoder.encode(fixtures.safePassword()));
        userAccountRepository.save(user);
    }
}
