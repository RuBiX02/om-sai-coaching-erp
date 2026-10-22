package com.omsai.coaching.demo;

import com.omsai.coaching.batch.Batch;
import com.omsai.coaching.batch.BatchForm;
import com.omsai.coaching.batch.BatchRepository;
import com.omsai.coaching.batch.BatchService;
import com.omsai.coaching.batch.BatchSubject;
import com.omsai.coaching.batch.BatchSubjectRepository;
import com.omsai.coaching.batch.CourseSubject;
import com.omsai.coaching.batch.CourseSubjectRepository;
import com.omsai.coaching.config.AppProperties;
import com.omsai.coaching.course.Course;
import com.omsai.coaching.course.CourseForm;
import com.omsai.coaching.course.CourseRepository;
import com.omsai.coaching.course.CourseService;
import com.omsai.coaching.enrollment.EnrollForm;
import com.omsai.coaching.enrollment.Enrollment;
import com.omsai.coaching.enrollment.EnrollmentRepository;
import com.omsai.coaching.enrollment.EnrollmentService;
import com.omsai.coaching.student.Student;
import com.omsai.coaching.student.StudentForm;
import com.omsai.coaching.student.StudentRepository;
import com.omsai.coaching.student.StudentService;
import com.omsai.coaching.subject.Subject;
import com.omsai.coaching.subject.SubjectForm;
import com.omsai.coaching.subject.SubjectRepository;
import com.omsai.coaching.subject.SubjectService;
import com.omsai.coaching.teacher.Teacher;
import com.omsai.coaching.teacher.TeacherForm;
import com.omsai.coaching.teacher.TeacherRepository;
import com.omsai.coaching.teacher.TeacherService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Opt-in seeder of the curated demo dataset.
 *
 * <p>It writes the catalogue of {@link DemoCatalog} — subjects, the
 * course offerings, the course-subject mappings, the teachers, the
 * students, the batches and the enrollments — into the database, and
 * nothing else. It never creates a fee agreement, a payment, a receipt,
 * an installment, a class session, an attendance mark or an
 * examination result: the dashboard must not look busy because money
 * and marks were invented.</p>
 *
 * <p><strong>Safety.</strong> Three independent guards have to agree
 * before a single row is written:</p>
 * <ol>
 *   <li>{@code app.demo.enabled} must be true. It is false by default
 *       and the production profile pins it to false, so a production
 *       database can never be seeded with demo data by configuration
 *       alone;</li>
 *   <li>the {@code prod} profile must not be active — the seeder
 *       refuses even if the flag says otherwise;</li>
 *   <li>{@code app.demo.target-database} must name the database the
 *       application is actually connected to, so the operator has to
 *       state the target explicitly.</li>
 * </ol>
 *
 * <p><strong>Idempotence.</strong> Every record is looked up by its
 * natural key first (the name of a subject, of a course, of a teacher
 * or of a student, the course and batch of a batch-subject
 * assignment, the student and batch of an enrollment). A second run
 * therefore creates nothing and changes nothing — the existing
 * demonstration students and every other record in the database are
 * left exactly as they are.</p>
 */
@Component
public class DemoDataSeeder implements CommandLineRunner {

    private static final Logger LOG = LoggerFactory.getLogger(DemoDataSeeder.class);

    /**
     * The contact details of the demo records are deliberately
     * synthetic: Indian mobile numbers always start with 6 to 9, so a
     * number beginning with two zeroes can never be a real mobile line
     * and can never reach anybody. The mail addresses use the reserved
     * domain {@code example.invalid}, which by RFC 2606 can never
     * resolve, so no message can leave the machine.
     */
    private static final String SYNTHETIC_PHONE_PREFIX = "00";

    private final AppProperties properties;
    private final Environment environment;
    private final JdbcTemplate jdbc;

    private final SubjectService subjectService;
    private final SubjectRepository subjectRepository;
    private final CourseService courseService;
    private final CourseRepository courseRepository;
    private final CourseSubjectRepository courseSubjectRepository;
    private final BatchService batchService;
    private final BatchRepository batchRepository;
    private final BatchSubjectRepository batchSubjectRepository;
    private final TeacherService teacherService;
    private final TeacherRepository teacherRepository;
    private final StudentService studentService;
    private final StudentRepository studentRepository;
    private final EnrollmentService enrollmentService;
    private final EnrollmentRepository enrollmentRepository;

    public DemoDataSeeder(AppProperties properties,
                          Environment environment,
                          JdbcTemplate jdbc,
                          SubjectService subjectService,
                          SubjectRepository subjectRepository,
                          CourseService courseService,
                          CourseRepository courseRepository,
                          CourseSubjectRepository courseSubjectRepository,
                          BatchService batchService,
                          BatchRepository batchRepository,
                          BatchSubjectRepository batchSubjectRepository,
                          TeacherService teacherService,
                          TeacherRepository teacherRepository,
                          StudentService studentService,
                          StudentRepository studentRepository,
                          EnrollmentService enrollmentService,
                          EnrollmentRepository enrollmentRepository) {
        this.properties = properties;
        this.environment = environment;
        this.jdbc = jdbc;
        this.subjectService = subjectService;
        this.subjectRepository = subjectRepository;
        this.courseService = courseService;
        this.courseRepository = courseRepository;
        this.courseSubjectRepository = courseSubjectRepository;
        this.batchService = batchService;
        this.batchRepository = batchRepository;
        this.batchSubjectRepository = batchSubjectRepository;
        this.teacherService = teacherService;
        this.teacherRepository = teacherRepository;
        this.studentService = studentService;
        this.studentRepository = studentRepository;
        this.enrollmentService = enrollmentService;
        this.enrollmentRepository = enrollmentRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        String refusal = refusalReason();
        if (refusal != null) {
            LOG.warn("Demo dataset: not seeded - {}. Nothing was written.", refusal);
            return;
        }

        LOG.info("Demo dataset: seeding the curated catalogue into '{}'.", currentDatabase());
        SeedSummary summary = seed();
        LOG.info("Demo dataset: done. Created {} subject(s), {} course(s), {} teacher(s), {} student(s), "
                        + "{} batch(es) and {} enrollment(s). Already present: {} subject(s), {} course(s), "
                        + "{} teacher(s), {} student(s), {} batch(es) and {} enrollment(s).",
                summary.createdSubjects(), summary.createdCourses(), summary.createdTeachers(),
                summary.createdStudents(), summary.createdBatches(), summary.createdEnrollments(),
                summary.existingSubjects(), summary.existingCourses(), summary.existingTeachers(),
                summary.existingStudents(), summary.existingBatches(), summary.existingEnrollments());
    }

    /**
     * Why the seeder must not write anything, or {@code null} when it
     * may run. The three guards are independent: the flag, the profile
     * and the named target database.
     */
    String refusalReason() {
        if (!properties.getDemo().isEnabled()) {
            return "app.demo.enabled is false";
        }
        if (environment.matchesProfiles("prod")) {
            return "the prod profile is active; a production database must never be seeded with demo data";
        }
        String expected = properties.getDemo().getTargetDatabase();
        if (expected == null || expected.isBlank()) {
            return "app.demo.target-database is not set; name the database this dataset may be written to";
        }
        String connected = currentDatabase();
        if (!expected.trim().equalsIgnoreCase(connected)) {
            return "app.demo.target-database is '" + expected.trim()
                    + "' but the application is connected to '" + connected + "'";
        }
        return null;
    }

    /** What the seeder created and what it found already in place. */
    public record SeedSummary(int createdSubjects, int createdCourses, int createdTeachers,
                               int createdStudents, int createdBatches, int createdEnrollments,
                               int existingSubjects, int existingCourses, int existingTeachers,
                               int existingStudents, int existingBatches, int existingEnrollments) {
    }

    /**
     * Writes the catalogue. Every step looks the record up first, so
     * running the seeder twice changes nothing the second time.
     */
    SeedSummary seed() {
        int createdSubjects = 0;
        int createdCourses = 0;
        int createdTeachers = 0;
        int createdStudents = 0;
        int createdBatches = 0;
        int createdEnrollments = 0;
        int existingSubjects = 0;
        int existingCourses = 0;
        int existingTeachers = 0;
        int existingStudents = 0;
        int existingBatches = 0;
        int existingEnrollments = 0;

        // ----- subjects -----
        Map<String, Subject> subjects = new HashMap<>();
        for (DemoCatalog.SubjectSeed seed : DemoCatalog.SUBJECTS) {
            Optional<Subject> found = subjectRepository.findByNameIgnoreCase(seed.name());
            if (found.isPresent()) {
                subjects.put(seed.name(), found.get());
                existingSubjects++;
                continue;
            }
            SubjectForm form = new SubjectForm();
            form.setName(seed.name());
            form.setDescription(seed.description());
            subjects.put(seed.name(), subjectService.create(form));
            createdSubjects++;
        }

        // ----- teachers -----
        Map<String, Teacher> teachers = new HashMap<>();
        int teacherIndex = 0;
        for (DemoCatalog.TeacherSeed seed : DemoCatalog.TEACHERS) {
            teacherIndex++;
            Optional<Teacher> found = teacherRepository.findByFullNameIgnoreCase(seed.fullName());
            if (found.isPresent()) {
                teachers.put(seed.fullName(), found.get());
                existingTeachers++;
                continue;
            }
            TeacherForm form = new TeacherForm();
            form.setFullName(seed.fullName());
            form.setPhone(syntheticPhone(teacherIndex));
            form.setEmail(emailLocalPart(seed.fullName()) + "@example.invalid");
            teachers.put(seed.fullName(), teacherService.create(form));
            createdTeachers++;
        }

        // ----- courses, their subjects and their batches -----
        Map<String, Course> courses = new LinkedHashMap<>();
        Map<String, Batch> batches = new LinkedHashMap<>();
        for (DemoCatalog.CourseSeed seed : DemoCatalog.COURSES) {
            Optional<Course> found = courseRepository.findByNameIgnoreCase(seed.name());
            Course course;
            if (found.isPresent()) {
                course = found.get();
                existingCourses++;
            } else {
                CourseForm form = new CourseForm();
                form.setName(seed.name());
                form.setCategory(seed.category());
                form.setDescription(seed.description());
                form.setDurationMonths(seed.durationMonths());
                course = courseService.create(form);
                createdCourses++;
            }
            courses.put(seed.name(), course);

            // The course-subject assignments.
            for (String subjectName : seed.subjects()) {
                Subject subject = subjects.get(subjectName);
                if (subject == null) {
                    continue;
                }
                if (!courseSubjectRepository.existsByCourseIdAndSubjectId(course.getId(), subject.getId())) {
                    courseService.assignSubject(course.getId(), subject.getId());
                }
            }

            // One batch per offering.
            String batchName = DemoCatalog.batchNameFor(seed);
            Optional<Batch> batchFound = batchRepository.findSameNameBatch(
                    course.getId(), DemoCatalog.ACADEMIC_YEAR, batchName);
            Batch batch;
            if (batchFound.isPresent()) {
                batch = batchFound.get();
                existingBatches++;
            } else {
                batch = createBatch(course, batchName);
                createdBatches++;
            }
            batches.put(seed.name(), batch);

            // The teaching assignments of that batch, with the teacher
            // of the subject area.
            for (CourseSubject link : courseSubjectRepository.findForCourse(course.getId())) {
                Optional<BatchSubject> assignment = batchSubjectRepository
                        .findByBatchIdAndCourseSubjectId(batch.getId(), link.getId());
                if (assignment.isEmpty()) {
                    batchService.addSubject(batch.getId(), link.getId());
                    assignment = batchSubjectRepository
                            .findByBatchIdAndCourseSubjectId(batch.getId(), link.getId());
                }
                if (assignment.isEmpty()) {
                    continue;
                }
                BatchSubject batchSubject = assignment.get();
                if (batchSubject.getTeacher() != null) {
                    continue;
                }
                Teacher teacher = teacherFor(teachers, link.getSubject().getName());
                if (teacher != null) {
                    batchService.assignTeacher(batchSubject.getId(), teacher.getId());
                }
            }
        }

        // ----- students and their enrollments -----
        int studentIndex = 0;
        for (DemoCatalog.StudentSeed seed : DemoCatalog.STUDENTS) {
            studentIndex++;
            Optional<Student> found = studentRepository.findByFullNameIgnoreCase(seed.fullName());
            Student student;
            if (found.isPresent()) {
                student = found.get();
                existingStudents++;
            } else {
                StudentForm form = new StudentForm();
                form.setFullName(seed.fullName());
                form.setPhone(syntheticPhone(studentIndex));
                form.setAddress(seed.address());
                form.setAdmissionDate(LocalDate.of(2026, 6, 1));
                student = studentService.create(form);
                createdStudents++;
            }

            for (String courseName : seed.courses()) {
                Batch batch = batches.get(courseName);
                if (batch == null) {
                    continue;
                }
                if (enrollmentRepository.existsByStudentIdAndBatchIdAndStatus(
                        student.getId(), batch.getId(), Enrollment.Status.ACTIVE)) {
                    existingEnrollments++;
                    continue;
                }
                EnrollForm form = new EnrollForm();
                form.setStudentId(student.getId());
                form.setBatchId(batch.getId());
                form.setEnrollmentDate(LocalDate.of(2026, 6, 1));
                enrollmentService.enroll(form);
                createdEnrollments++;
            }
        }

        return new SeedSummary(createdSubjects, createdCourses, createdTeachers, createdStudents,
                createdBatches, createdEnrollments, existingSubjects, existingCourses,
                existingTeachers, existingStudents, existingBatches, existingEnrollments);
    }

    /** The teacher of the subject area, if the catalogue defines one. */
    private Teacher teacherFor(Map<String, Teacher> teachers, String subjectName) {
        for (DemoCatalog.TeacherSeed seed : DemoCatalog.TEACHERS) {
            if (seed.subjects().contains(subjectName)) {
                return teachers.get(seed.fullName());
            }
        }
        return null;
    }

    private Batch createBatch(Course course, String batchName) {
        BatchForm form = new BatchForm();
        form.setCourseId(course.getId());
        form.setName(batchName);
        form.setAcademicYear(DemoCatalog.ACADEMIC_YEAR);
        form.setStartDate(LocalDate.of(2026, 6, 1));
        form.setEndDate(LocalDate.of(2027, 3, 31));
        return batchService.create(form);
    }

    private String currentDatabase() {
        return jdbc.queryForObject("select current_database()", String.class);
    }

    /**
     * A contact number that cannot belong to a real person: Indian
     * mobile numbers always begin with 6 to 9, so this one can never be
     * dialled.
     */
    static String syntheticPhone(int index) {
        return SYNTHETIC_PHONE_PREFIX + String.format("%08d", index);
    }

    /** A mail address on the reserved domain that can never resolve. */
    static String emailLocalPart(String fullName) {
        return fullName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", ".");
    }
}
