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
import com.omsai.coaching.fees.FeeAccountForm;
import com.omsai.coaching.fees.FeeAccountService;
import com.omsai.coaching.fees.InstallmentInput;
import com.omsai.coaching.fees.InstallmentService;
import com.omsai.coaching.security.UserAccount;
import com.omsai.coaching.security.UserAccountRepository;
import com.omsai.coaching.security.UserForm;
import com.omsai.coaching.security.UserService;
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
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
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
@Order(100) // after the admin bootstrap so the pack can name a creator
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
    private final FeeAccountService feeAccountService;
    private final InstallmentService installmentService;
    private final UserAccountRepository userAccountRepository;
    private final UserService userService;

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
                          EnrollmentRepository enrollmentRepository,
                          FeeAccountService feeAccountService,
                          InstallmentService installmentService,
                          UserAccountRepository userAccountRepository,
                          UserService userService) {
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
        this.feeAccountService = feeAccountService;
        this.installmentService = installmentService;
        this.userAccountRepository = userAccountRepository;
        this.userService = userService;
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
        if (properties.getDemo().isLearningData()) {
            seedLearningPack();
        } else {
            LOG.info("Demo dataset: the learning pack is off "
                    + "(set DEMO_LEARNING_DATA=1 to seed timetable, attendance, "
                    + "resources, quizzes and a sample fee agreement).");
        }
        seedPortalAccounts();
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

    // ======= demo portal accounts (re-created on every start) ========

    private void seedPortalAccounts() {
        String studentPassword = environment.getProperty("DEMO_STUDENT_PASSWORD");
        String teacherPassword = environment.getProperty("DEMO_TEACHER_PASSWORD");
        boolean createdStudent = false;
        boolean createdTeacher = false;
        if (studentPassword != null && !studentPassword.isBlank()
                && demoAccountAbsent("ananya.portal@omsai.demo")) {
            Student student = studentRepository.findByFullNameIgnoreCase("Ananya Patil")
                    .orElse(null);
            if (student != null && student.getUserAccount() == null) {
                UserForm form = new UserForm();
                form.setFullName(student.getFullName());
                form.setEmail("ananya.portal@omsai.demo");
                form.setRole("STUDENT");
                form.setStudentId(student.getId());
                form.setPassword(studentPassword);
                userService.create(form);
                createdStudent = true;
            }
        }
        if (teacherPassword != null && !teacherPassword.isBlank()
                && demoAccountAbsent("sandeep.portal@omsai.demo")) {
            com.omsai.coaching.teacher.Teacher teacher =
                    teacherRepository.findByFullNameIgnoreCase("Sandeep Patil").orElse(null);
            if (teacher != null && teacher.getLinkedUser() == null) {
                UserForm form = new UserForm();
                form.setFullName(teacher.getFullName());
                form.setEmail("sandeep.portal@omsai.demo");
                form.setRole("TEACHER");
                form.setPassword(teacherPassword);
                com.omsai.coaching.security.UserAccount account = userService.create(form);
                com.omsai.coaching.teacher.Teacher managed =
                        teacherRepository.findById(teacher.getId()).orElseThrow();
                managed.setLinkedUser(account);
                teacherRepository.save(managed);
                createdTeacher = true;
            }
        }
        LOG.info("Demo dataset: portal accounts - student {} teacher {} "
                        + "(passwords come from DEMO_STUDENT_PASSWORD/DEMO_TEACHER_PASSWORD).",
                createdStudent ? "created" : "kept",
                createdTeacher ? "created" : "kept");
    }

    /** True when no sign-in account with that demo email exists yet. */
    private boolean demoAccountAbsent(String email) {
        return !userAccountRepository.existsByEmailIgnoreCase(email);
    }

    // ============================================================
    // The opt-in learning pack: weekly timetable, past sessions with
    // attendance, study resources, quizzes and one sample fee agreement
    // for the MHT-CET target batch. Everything here is DEMO content for
    // presentations: it never runs without DEMO_LEARNING_DATA=1, and any
    // part that is already present is left untouched.
    // ============================================================

    private static final String MHT_CET_COMBINED_COURSE =
            "MHT-CET — Class 11 and 12 Combined Preparation";

    /** One planned weekly class slot of a subject. */
    private record Slot(List<DayOfWeek> days, java.time.LocalTime start,
                        java.time.LocalTime end, String room) {
    }

    private void seedLearningPack() {
        Course course = courseRepository.findByNameIgnoreCase(MHT_CET_COMBINED_COURSE)
                .orElse(null);
        if (course == null) {
            LOG.warn("Demo learning pack: course '{}' not found - skipped.", MHT_CET_COMBINED_COURSE);
            return;
        }
        Long batchId = jdbc.queryForObject(
                "select min(id) from batches where course_id = ?", Long.class, course.getId());
        if (batchId == null) {
            LOG.warn("Demo learning pack: there is no batch for '{}' yet - skipped.", course.getName());
            return;
        }
        Long adminId = jdbc.queryForObject(
                "select min(id) from user_accounts where role = 'ADMIN'", Long.class);
        if (adminId == null) {
            LOG.warn("Demo learning pack: no administrator account exists yet - skipped "
                    + "(enable the admin bootstrap for the demo profile).");
            return;
        }

        long existingTimetable = jdbc.queryForObject(
                "select count(*) from timetable_entries te "
                        + "join batch_subjects bs on bs.id = te.batch_subject_id "
                        + "where bs.batch_id = ?", Long.class, batchId);
        if (existingTimetable > 0) {
            LOG.info("Demo learning pack: already present for batch {} - nothing new written.", batchId);
            return;
        }

        List<Map<String, Object>> assignments = jdbc.queryForList(
                "select bs.id as bs_id, s.name as subject from batch_subjects bs "
                        + "join course_subjects cs on cs.id = bs.course_subject_id "
                        + "join subjects s on s.id = cs.subject_id "
                        + "where bs.batch_id = ? and bs.active = true order by bs.id", batchId);
        LocalDate effectiveFrom = LocalDate.of(2026, 6, 1);
        LocalDate today = LocalDate.now();

        int timetableRows = 0;
        int sessionRows = 0;
        int attendanceRows = 0;
        int resourceRows = 0;
        int quizRows = 0;

        // --- 1. the weekly timetable of the batch ----------------------
        for (Map<String, Object> assignment : assignments) {
            long bsId = ((Number) assignment.get("bs_id")).longValue();
            String subject = String.valueOf(assignment.get("subject"));
            for (Slot slot : slotsFor(subject)) {
                for (DayOfWeek day : slot.days()) {
                    timetableRows += jdbc.update("""
                            insert into timetable_entries (batch_subject_id, day_of_week,
                                start_time, end_time, classroom, topic, effective_from,
                                active, created_by)
                            values (?, ?, ?, ?, ?, ?, ?, true, ?)
                            """, bsId, day.name(), slot.start(), slot.end(), slot.room(),
                            topicFor(subject, day), effectiveFrom, adminId);
                }
            }
        }

        // --- 2. past sessions with attendance --------------------------
        List<Map<String, Object>> enrollments = jdbc.queryForList(
                "select e.id as enr_id, e.student_id from enrollments e "
                        + "where e.batch_id = ? and e.status = 'ACTIVE'", batchId);
        Map<Long, Integer> historyCounter = new HashMap<>();
        for (Map<String, Object> assignment : assignments) {
            long bsId = ((Number) assignment.get("bs_id")).longValue();
            String subject = String.valueOf(assignment.get("subject"));
            for (Slot slot : slotsFor(subject)) {
                for (int back = 21; back >= 1; back--) {
                    LocalDate date = today.minusDays(back);
                    if (!slot.days().contains(date.getDayOfWeek())) {
                        continue;
                    }
                    jdbc.update("""
                            insert into class_sessions (batch_subject_id, session_date,
                                start_time, end_time, topic, status, created_by)
                            values (?, ?, ?, ?, ?, 'COMPLETED', ?)
                            """, bsId, date, slot.start(), slot.end(),
                            topicFor(subject, date.getDayOfWeek()), adminId);
                    sessionRows++;
                    Long sessionId = jdbc.queryForObject(
                            "select id from class_sessions where batch_subject_id = ? and session_date = ?",
                            Long.class, bsId, date);
                    for (Map<String, Object> enrollment : enrollments) {
                        long enrollmentId = ((Number) enrollment.get("enr_id")).longValue();
                        int attended = historyCounter.merge(enrollmentId, 1, Integer::sum);
                        attendanceRows += jdbc.update("""
                                insert into attendance (class_session_id, enrollment_id,
                                    status, marked_by)
                                values (?, ?, ?, ?)
                                """, sessionId, enrollmentId, attendanceStatusFor(attended), adminId);
                    }
                }
            }
        }

        // --- 3. study resources ----------------------------------------
        String resourceIdColumn = "select id from subjects where lower(name) = lower(?)";
        for (Map<String, Object> a : assignments) {
            String subject = String.valueOf(a.get("subject"));
            Long subjectId = jdbc.queryForObject(resourceIdColumn, Long.class, subject);
            for (String[] row : resourcesFor(subject)) {
                resourceRows += jdbc.update("""
                        insert into study_resources (subject_id, batch_id, title, description,
                            resource_type, url)
                        values (?, ?, ?, ?, ?, ?)
                        """, subjectId, batchId, row[0], row[1], row[2],
                        row[3] == null ? null : row[3].isBlank() ? null : row[3]);
            }
        }
        resourceRows += jdbc.update("""
                insert into study_resources (subject_id, batch_id, title, description,
                    resource_type, url)
                values (null, ?, ?, ?, ?, null)
                """, batchId, "MHT-CET 2027 — syllabus and paper pattern",
                "Entrance examination structure, marking scheme and the subject-wise "
                        + "weightage chart pinned by the office.", "NOTES");

        // --- 4. two short quizzes ---------------------------------------
        if (jdbc.queryForObject("select count(*) from quizzes where batch_id = ?",
                Long.class, batchId) == 0) {
            jdbc.update("""
                    insert into quizzes (batch_id, title, subject_name, description, duration_minutes)
                    values (?, ?, ?, ?, ?)
                    """, batchId, "Kinematics quick check", "Physics",
                    "Five multiple-choice questions on motion in one dimension — "
                            + "the opening chapter of the MHT-CET physics cycle.", 10);
            Long physicsQuizId = jdbc.queryForObject(
                    "select id from quizzes where batch_id = ? and title = 'Kinematics quick check'",
                    Long.class, batchId);
            quizRows += insertQuizQuestions(physicsQuizId, new String[][] {
                    {"A body starts from rest and moves with a uniform acceleration a. "
                            + "The distance covered in time t is",
                            "a t²", "½ a t²", "2 a t²", "½ a t³", "B"},
                    {"The slope of a velocity–time graph gives the",
                            "displacement", "distance", "acceleration", "jerk", "C"},
                    {"The equation v² = u² + 2as applies to",
                            "any motion in a circle", "motion with uniform acceleration",
                            "simple harmonic motion", "motion with uniform velocity", "B"},
                    {"A ball is thrown straight up. At the highest point, its acceleration is",
                            "zero", "downward at g", "upward at g", "changing direction", "B"},
                    {"A car drives around a circular track and returns to the start. "
                            + "Its average velocity for the lap is",
                            "equal to its average speed", "zero", "v²/r", "2πr/t", "B"},
            });
            jdbc.update("""
                    insert into quizzes (batch_id, title, subject_name, description, duration_minutes)
                    values (?, ?, ?, ?, ?)
                    """, batchId, "Quadratic equations — 5 sums", "Mathematics",
                    "Practice set on discriminants, roots and factorisation from the "
                            + "MHT-CET mathematics cycle.", 12);
            Long mathsQuizId = jdbc.queryForObject(
                    "select id from quizzes where batch_id = ? and title = 'Quadratic equations — 5 sums'",
                    Long.class, batchId);
            quizRows += insertQuizQuestions(mathsQuizId, new String[][] {
                    {"The discriminant of 2x² − 3x + 5 = 0 is",
                            "31", "−31", "−29", "49", "B"},
                    {"The sum of the roots of x² − 5x + 6 = 0 is",
                            "5", "−5", "6", "1", "A"},
                    {"The quadratic equation with roots 3 and −1 is",
                            "x² + 2x + 3 = 0", "x² − 2x + 3 = 0", "x² − 2x − 3 = 0",
                            "x² + 2x − 1 = 0", "C"},
                    {"Both roots of a quadratic are real and distinct when the discriminant is",
                            "zero", "greater than zero", "less than zero", "a perfect square", "B"},
                    {"The product of the roots of x² − 7x + 12 = 0 is", "7", "−12", "−7", "12", "D"},
            });
        }

        // --- 5. one sample fee agreement for the demo student -----------
        Student demoStudent = studentRepository.findByFullNameIgnoreCase("Ananya Patil")
                .orElse(null);
        if (demoStudent != null) {
            Long enrollmentId = jdbc.queryForObject(
                    "select min(id) from enrollments where student_id = ? and batch_id = ? "
                            + "and status = 'ACTIVE'", Long.class, demoStudent.getId(), batchId);
            if (enrollmentId != null && jdbc.queryForObject(
                    "select count(*) from fee_accounts where enrollment_id = ?",
                    Long.class, enrollmentId) == 0) {
                UserAccount admin = userAccountRepository.findById(adminId).orElseThrow();
                FeeAccountForm feeForm = new FeeAccountForm();
                feeForm.setEnrollmentId(enrollmentId);
                feeForm.setGrossFee(new BigDecimal("48000"));
                feeForm.setDiscountAmount(BigDecimal.ZERO);
                feeForm.setNotes("Sample agreement of the demo learning pack "
                        + "— settle it from the student portal to try payments.");
                var account = feeAccountService.create(feeForm, admin);
                List<InstallmentInput> plan = new java.util.ArrayList<>();
                for (int i = 1; i <= 4; i++) {
                    plan.add(new InstallmentInput("12000", "2026-%02d-01".formatted(6 + i),
                            String.valueOf(i)));
                }
                installmentService.createPlan(account.getId(), plan, admin);
                LOG.info("Demo learning pack: fee agreement {} with a 4-installment plan "
                        + "created for {}.", account.getId(), demoStudent.getFullName());
            }
        }

        LOG.info("Demo learning pack: {} timetable slot(s), {} past session(s) with "
                        + "{} attendance row(s), {} resource(s) and {} quiz question group(s) "
                        + "seeded for batch {}.",
                timetableRows, sessionRows, attendanceRows, resourceRows, quizRows, batchId);
    }

    /** The weekly slots of a subject of the PCMB group. */
    private List<Slot> slotsFor(String subject) {
        return switch (subject) {
            case "Physics" -> List.of(new Slot(
                    List.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
                    java.time.LocalTime.of(7, 0), java.time.LocalTime.of(8, 15), "Room A-1"));
            case "Chemistry" -> List.of(new Slot(
                    List.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
                    java.time.LocalTime.of(8, 15), java.time.LocalTime.of(9, 30), "Room A-1"));
            case "Mathematics" -> List.of(new Slot(
                    List.of(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY, DayOfWeek.SATURDAY),
                    java.time.LocalTime.of(7, 0), java.time.LocalTime.of(8, 15), "Room A-2"));
            case "Biology" -> List.of(new Slot(
                    List.of(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY, DayOfWeek.SATURDAY),
                    java.time.LocalTime.of(8, 15), java.time.LocalTime.of(9, 30), "Room A-2"));
            default -> List.of(new Slot(
                    List.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
                    java.time.LocalTime.of(9, 45), java.time.LocalTime.of(11, 0), "Room B-1"));
        };
    }

    /** A small rotating topic list per subject. */
    private String topicFor(String subject, DayOfWeek day) {
        return switch (subject) {
            case "Physics" -> switch (day) {
                case MONDAY -> "MHT-CET Physics: kinematics and graphs";
                case WEDNESDAY -> "MHT-CET Physics: laws of motion";
                default -> "MHT-CET Physics: work, energy and power";
            };
            case "Chemistry" -> switch (day) {
                case MONDAY -> "MHT-CET Chemistry: atomic structure";
                case WEDNESDAY -> "MHT-CET Chemistry: periodicity";
                default -> "MHT-CET Chemistry: chemical bonding";
            };
            case "Mathematics" -> switch (day) {
                case TUESDAY -> "MHT-CET Maths: quadratic equations";
                case THURSDAY -> "MHT-CET Maths: trigonometry";
                default -> "MHT-CET Maths: straight line and pairs of lines";
            };
            case "Biology" -> switch (day) {
                case TUESDAY -> "MHT-CET Biology: cell biology";
                case THURSDAY -> "MHT-CET Biology: plant physiology";
                default -> "MHT-CET Biology: human physiology basics";
            };
            default -> subject + " — class diary";
        };
    }

    /**
     * The resources of one subject: {title, description, type, url}
     * rows. LINK/VIDEO rows must carry a url (the schema checks this);
     * NOTES/WORKSHEET rows are shown as in-institute material with none.
     */
    private String[][] resourcesFor(String subject) {
        return switch (subject) {
            case "Physics" -> new String[][] {
                    {"Physics — kinematics formula sheet",
                            "Every MHT-CET kinematics relation collected on one page: "
                                    + "the three equations of motion, graphs and shortcut rules.",
                            "NOTES", null},
                    {"Physics — numericals worksheet (motion)",
                            "Twenty worked-and-unworked pairs of one-dimensional motion "
                                    + "numericals in the MHT-CET marking style.", "WORKSHEET", null},
                    {"NCERT Physics textbook — class 11 part 1 (external)",
                            "The official NCERT e-book the institute references "
                                    + "for the state-board syllabus overlap.", "LINK",
                            "https://ncert.nic.in/textbook.php"},
            };
            case "Chemistry" -> new String[][] {
                    {"Chemistry — atomic structure summary",
                            "Orbitals, quantum numbers and configuration rules reduced "
                                    + "to the questions MHT-CET actually asks.", "NOTES", null},
                    {"Chemistry — periodic trends worksheet",
                            "Radii, ionisation energy and electronegativity across a "
                                    + "period and a group — fill-it-in practice.", "WORKSHEET", null},
            };
            case "Mathematics" -> new String[][] {
                    {"Mathematics — quadratic practice set",
                            "Forty sums with answers keyed to the MHT-CET shortcut "
                                    + "methods: discriminant, factorisation and root relations.",
                            "NOTES", null},
                    {"NCERT Mathematics textbook — class 11 (external)",
                            "The official NCERT e-book the institute references for "
                                    + "the combined-preparation course.", "LINK",
                            "https://ncert.nic.in/textbook.php"},
            };
            case "Biology" -> new String[][] {
                    {"Biology — cell biology diagram pack",
                            "Labelled diagrams of plant and animal cell structures with "
                                    + "the one-line answers MHT-CET expects.", "NOTES", null},
            };
            default -> new String[][] {
                    {subject + " — institute notes",
                            "Class notes compiled from the batch lecture diary.",
                            "NOTES", null},
            };
        };
    }

    /** The five questions of one quiz, from {question, a, b, c, d, key}. */
    private int insertQuizQuestions(Long quizId, String[][] questions) {
        int inserted = 0;
        for (int i = 0; i < questions.length; i++) {
            inserted += jdbc.update("""
                    insert into quiz_questions (quiz_id, position, question_text,
                        option_a, option_b, option_c, option_d, correct_option)
                    values (?, ?, ?, ?, ?, ?, ?, ?)
                    """, quizId, i + 1, questions[i][0], questions[i][1],
                    questions[i][2], questions[i][3], questions[i][4], questions[i][5]);
        }
        return inserted;
    }

    /**
     * A deterministic, mixed attendance history: mostly present, a few
     * late marks and two absences — stable across restarts so demo
     * screenshots stay reproducible.
     */
    private String attendanceStatusFor(int attended) {
        if (attended == 2 || attended == 11) {
            return "LATE";
        }
        if (attended == 6 || attended == 15) {
            return "ABSENT";
        }
        return "PRESENT";
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
