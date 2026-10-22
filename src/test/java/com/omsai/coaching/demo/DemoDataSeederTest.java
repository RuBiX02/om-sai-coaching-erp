package com.omsai.coaching.demo;

import com.omsai.coaching.batch.BatchRepository;
import com.omsai.coaching.batch.CourseSubjectRepository;
import com.omsai.coaching.config.AppProperties;
import com.omsai.coaching.course.CourseRepository;
import com.omsai.coaching.enrollment.EnrollmentRepository;
import com.omsai.coaching.fees.FeeAccountRepository;
import com.omsai.coaching.fees.PaymentRepository;
import com.omsai.coaching.student.StudentRepository;
import com.omsai.coaching.subject.SubjectRepository;
import com.omsai.coaching.teacher.TeacherRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The demo seeder: the guards that keep it away from a production or
 * unnamed database, the completeness of the curated catalogue and the
 * idempotence of a second run.
 *
 * <p>The tests run against the disposable {@code omsai_test} database
 * inside a rolled-back transaction, so nothing they write survives.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DemoDataSeederTest {

    @Autowired
    private DemoDataSeeder seeder;

    @Autowired
    private AppProperties properties;

    @Autowired
    private Environment environment;

    @Autowired
    private SubjectRepository subjectRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private CourseSubjectRepository courseSubjectRepository;

    @Autowired
    private TeacherRepository teacherRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private BatchRepository batchRepository;

    @Autowired
    private EnrollmentRepository enrollmentRepository;

    @Autowired
    private FeeAccountRepository feeAccountRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    private boolean originalEnabled;
    private String originalTarget;

    @BeforeEach
    void rememberConfiguration() {
        originalEnabled = properties.getDemo().isEnabled();
        originalTarget = properties.getDemo().getTargetDatabase();
    }

    @AfterEach
    void restoreConfiguration() {
        properties.getDemo().setEnabled(originalEnabled);
        properties.getDemo().setTargetDatabase(originalTarget);
    }

    // ----- the guards -------------------------------------------------------

    @Test
    void theSeederIsDisabledByDefault() {
        assertThat(properties.getDemo().isEnabled()).isFalse();
        assertThat(seeder.refusalReason()).isNotNull();
        assertThat(seeder.refusalReason()).contains("app.demo.enabled is false");
    }

    @Test
    void theSeederRefusesWithoutANamedTargetDatabase() {
        properties.getDemo().setEnabled(true);
        properties.getDemo().setTargetDatabase("");

        assertThat(seeder.refusalReason()).isNotNull();
        assertThat(seeder.refusalReason()).contains("target-database is not set");
    }

    @Test
    void theSeederRefusesWhenTheTargetDatabaseIsNotTheConnectedOne() {
        properties.getDemo().setEnabled(true);
        properties.getDemo().setTargetDatabase("some_other_database");

        assertThat(seeder.refusalReason()).isNotNull();
        assertThat(seeder.refusalReason()).contains("connected to");
    }

    @Test
    void theSeederRefusesInTheProductionProfile() {
        MockEnvironment prod = new MockEnvironment();
        prod.setActiveProfiles("prod");
        DemoDataSeeder productionSeeder = new DemoDataSeeder(properties, prod, null,
                subjectService, subjectRepository, courseService, courseRepository,
                courseSubjectRepository, batchService, batchRepository, batchSubjectRepository,
                teacherService, teacherRepository, studentService, studentRepository,
                enrollmentService, enrollmentRepository);

        properties.getDemo().setEnabled(true);
        properties.getDemo().setTargetDatabase("omsai_test");

        assertThat(productionSeeder.refusalReason()).isNotNull();
        assertThat(productionSeeder.refusalReason()).contains("prod profile");
    }

    @Test
    void aRefusedSeederWritesNothing() {
        properties.getDemo().setEnabled(false);
        long coursesBefore = courseRepository.count();
        long subjectsBefore = subjectRepository.count();

        seeder.run();

        assertThat(courseRepository.count()).isEqualTo(coursesBefore);
        assertThat(subjectRepository.count()).isEqualTo(subjectsBefore);
    }

    // ----- the catalogue ----------------------------------------------------

    @Test
    void theSeederCreatesEveryCatalogueSubject() {
        seed();

        for (DemoCatalog.SubjectSeed seed : DemoCatalog.SUBJECTS) {
            assertThat(subjectRepository.findByNameIgnoreCase(seed.name()))
                    .as("missing subject: " + seed.name())
                    .isPresent();
        }
    }

    @Test
    void theSeederCreatesEveryCatalogueCourse() {
        seed();

        for (DemoCatalog.CourseSeed seed : DemoCatalog.COURSES) {
            assertThat(courseRepository.findByNameIgnoreCase(seed.name()))
                    .as("missing course: " + seed.name())
                    .isPresent();
        }
    }

    @Test
    void everySeededCourseHasItsRequiredSubjects() {
        seed();

        for (DemoCatalog.CourseSeed seed : DemoCatalog.COURSES) {
            var course = courseRepository.findByNameIgnoreCase(seed.name()).orElseThrow();
            var assigned = courseSubjectRepository.findForCourse(course.getId()).stream()
                    .map(link -> link.getSubject().getName())
                    .toList();
            assertThat(assigned)
                    .as("wrong subjects for " + seed.name())
                    .containsExactlyInAnyOrderElementsOf(seed.subjects());
        }
    }

    @Test
    void theSeededStreamCoursesHaveTheRightSubjects() {
        seed();

        assertSubjectsOf("Standard 11 Science — PCM", "Physics", "Chemistry", "Mathematics", "English");
        assertSubjectsOf("Standard 11 Science — PCB", "Physics", "Chemistry", "Biology", "English");
        assertSubjectsOf("Standard 11 Science — PCMB",
                "Physics", "Chemistry", "Mathematics", "Biology", "English");
        assertSubjectsOf("JEE Main — PCM Preparation", "Physics", "Chemistry", "Mathematics");
        assertSubjectsOf("NEET-UG — Medical Entrance Preparation", "Physics", "Chemistry", "Biology");
        assertSubjectsOf("Engineering Mathematics I", "Engineering Mathematics");
    }

    @Test
    void theSeederCreatesTheTeachersAndTheStudents() {
        seed();

        for (DemoCatalog.TeacherSeed teacher : DemoCatalog.TEACHERS) {
            assertThat(teacherRepository.findByFullNameIgnoreCase(teacher.fullName()))
                    .as("missing teacher: " + teacher.fullName())
                    .isPresent();
        }
        for (DemoCatalog.StudentSeed student : DemoCatalog.STUDENTS) {
            assertThat(studentRepository.findByFullNameIgnoreCase(student.fullName()))
                    .as("missing student: " + student.fullName())
                    .isPresent();
        }
    }

    @Test
    void theSeederCreatesTheBatchesAndTheEnrollments() {
        seed();

        assertThat(batchRepository.count()).isGreaterThanOrEqualTo(DemoCatalog.COURSES.size());
        assertThat(enrollmentRepository.count()).isGreaterThanOrEqualTo(30);
    }

    @Test
    void theSeededTeachersGetTheirTeachingAssignments() {
        seed();

        var assignment = batchSubjectRepository.findAll().stream()
                .filter(bs -> bs.getTeacher() != null)
                .toList();
        assertThat(assignment)
                .as("the demo batches must have teachers")
                .isNotEmpty();
    }

    @Test
    void theSeederCreatesNoFinancialOrAcademicHistory() {
        seed();

        assertThat(feeAccountRepository.count()).isZero();
        assertThat(paymentRepository.count()).isZero();
    }

    @Test
    void theSeededContactDetailsAreSynthetic() {
        seed();

        var teacher = teacherRepository.findByFullNameIgnoreCase("Sandeep Patil").orElseThrow();
        assertThat(teacher.getPhone()).startsWith("00");
        assertThat(teacher.getEmail()).endsWith("@example.invalid");

        var student = studentRepository.findByFullNameIgnoreCase("Ananya Patil").orElseThrow();
        assertThat(student.getPhone()).startsWith("00");
    }

    @Test
    void theSeededStudentsHaveNoLoginAccount() {
        seed();

        for (DemoCatalog.StudentSeed seed : DemoCatalog.STUDENTS) {
            var student = studentRepository.findByFullNameIgnoreCase(seed.fullName()).orElseThrow();
            assertThat(student.getUserAccount())
                    .as("a demo student must not have a sign-in account")
                    .isNull();
        }
    }

    // ----- idempotence -----------------------------------------------------

    @Test
    void aSecondRunCreatesNothingNew() {
        DemoDataSeeder.SeedSummary first = seed();
        long courses = courseRepository.count();
        long subjects = subjectRepository.count();
        long teachers = teacherRepository.count();
        long students = studentRepository.count();
        long batches = batchRepository.count();
        long enrollments = enrollmentRepository.count();

        DemoDataSeeder.SeedSummary second = seed();

        assertThat(second.createdSubjects()).isZero();
        assertThat(second.createdCourses()).isZero();
        assertThat(second.createdTeachers()).isZero();
        assertThat(second.createdStudents()).isZero();
        assertThat(second.createdBatches()).isZero();
        assertThat(second.createdEnrollments()).isZero();

        assertThat(courseRepository.count()).isEqualTo(courses);
        assertThat(subjectRepository.count()).isEqualTo(subjects);
        assertThat(teacherRepository.count()).isEqualTo(teachers);
        assertThat(studentRepository.count()).isEqualTo(students);
        assertThat(batchRepository.count()).isEqualTo(batches);
        assertThat(enrollmentRepository.count()).isEqualTo(enrollments);

        // The first run created everything.
        assertThat(first.createdCourses()).isEqualTo(DemoCatalog.COURSES.size());
        assertThat(first.createdSubjects()).isEqualTo(DemoCatalog.SUBJECTS.size());
    }

    @Test
    void theSeederLeavesExistingUnrelatedDataAlone() {
        // A subject that is not part of the catalogue.
        var own = new com.omsai.coaching.subject.SubjectForm();
        own.setName("Untouched Subject " + System.nanoTime());
        subjectService.create(own);

        seed();

        assertThat(subjectRepository.findByNameIgnoreCase(own.getName())).isPresent();
    }

    // ----- helpers ----------------------------------------------------------

    private DemoDataSeeder.SeedSummary seed() {
        properties.getDemo().setEnabled(true);
        properties.getDemo().setTargetDatabase("omsai_test");
        return seeder.seed();
    }

    private void assertSubjectsOf(String courseName, String... expected) {
        var course = courseRepository.findByNameIgnoreCase(courseName).orElseThrow();
        var assigned = courseSubjectRepository.findForCourse(course.getId()).stream()
                .map(link -> link.getSubject().getName())
                .toList();
        assertThat(assigned).containsExactlyInAnyOrder(expected);
    }

    @Autowired
    private com.omsai.coaching.subject.SubjectService subjectService;

    @Autowired
    private com.omsai.coaching.course.CourseService courseService;

    @Autowired
    private com.omsai.coaching.batch.BatchService batchService;

    @Autowired
    private com.omsai.coaching.batch.BatchSubjectRepository batchSubjectRepository;

    @Autowired
    private com.omsai.coaching.teacher.TeacherService teacherService;

    @Autowired
    private com.omsai.coaching.student.StudentService studentService;

    @Autowired
    private com.omsai.coaching.enrollment.EnrollmentService enrollmentService;
}
