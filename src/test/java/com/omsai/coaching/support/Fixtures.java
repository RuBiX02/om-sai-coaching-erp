package com.omsai.coaching.support;

import com.omsai.coaching.batch.Batch;
import com.omsai.coaching.batch.BatchRepository;
import com.omsai.coaching.batch.BatchSubject;
import com.omsai.coaching.batch.BatchSubjectRepository;
import com.omsai.coaching.batch.CourseSubject;
import com.omsai.coaching.batch.CourseSubjectRepository;
import com.omsai.coaching.course.Course;
import com.omsai.coaching.course.CourseRepository;
import com.omsai.coaching.enrollment.Enrollment;
import com.omsai.coaching.enrollment.EnrollmentRepository;
import com.omsai.coaching.security.UserAccount;
import com.omsai.coaching.security.UserAccountRepository;
import com.omsai.coaching.student.Student;
import com.omsai.coaching.student.StudentRepository;
import com.omsai.coaching.subject.Subject;
import com.omsai.coaching.subject.SubjectRepository;
import com.omsai.coaching.teacher.Teacher;
import com.omsai.coaching.teacher.TeacherRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Helper that builds small, self-contained data fixtures for the service
 * and web-layer tests. Tests run inside a rolled-back transaction, so the
 * fixtures never persist between runs.
 */
@Component
public class Fixtures {

    private static final LocalDate DAY = LocalDate.of(2026, 9, 1);

    private final CourseRepository courseRepository;
    private final BatchRepository batchRepository;
    private final SubjectRepository subjectRepository;
    private final CourseSubjectRepository courseSubjectRepository;
    private final BatchSubjectRepository batchSubjectRepository;
    private final StudentRepository studentRepository;
    private final TeacherRepository teacherRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;

    public Fixtures(CourseRepository courseRepository,
                    BatchRepository batchRepository,
                    SubjectRepository subjectRepository,
                    CourseSubjectRepository courseSubjectRepository,
                    BatchSubjectRepository batchSubjectRepository,
                    StudentRepository studentRepository,
                    TeacherRepository teacherRepository,
                    EnrollmentRepository enrollmentRepository,
                    UserAccountRepository userAccountRepository,
                    PasswordEncoder passwordEncoder) {
        this.courseRepository = courseRepository;
        this.batchRepository = batchRepository;
        this.subjectRepository = subjectRepository;
        this.courseSubjectRepository = courseSubjectRepository;
        this.batchSubjectRepository = batchSubjectRepository;
        this.studentRepository = studentRepository;
        this.teacherRepository = teacherRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /** Creates an active course with a unique name for the current test. */
    public Course course(String name) {
        Course course = new Course();
        course.setName(name + " " + unique());
        course.setCategory("Academic");
        course.setDurationMonths(12);
        return courseRepository.save(course);
    }

    /** Creates a batch for the given course with a unique name. */
    public Batch batch(Course course, String name) {
        Batch batch = new Batch();
        batch.setCourse(course);
        batch.setName(name + " " + unique());
        batch.setAcademicYear("2026-27");
        batch.setStartDate(DAY);
        return batchRepository.save(batch);
    }

    /** Creates a subject with a unique name. */
    public Subject subject(String name) {
        Subject subject = new Subject();
        subject.setName(name + " " + unique());
        return subjectRepository.save(subject);
    }

    /** Assigns a subject to a course and returns the pairing row. */
    public CourseSubject assign(Course course, Subject subject) {
        CourseSubject cs = new CourseSubject();
        cs.setCourse(courseRepository.findById(course.getId()).orElseThrow());
        cs.setSubject(subjectRepository.findById(subject.getId()).orElseThrow());
        return courseSubjectRepository.save(cs);
    }

    /** Maps a course subject into a batch's timetable. */
    public BatchSubject assignCourseSubject(Batch batch, CourseSubject cs) {
        BatchSubject bs = new BatchSubject();
        bs.setBatch(batchRepository.findById(batch.getId()).orElseThrow());
        bs.setCourseSubject(courseSubjectRepository.findById(cs.getId()).orElseThrow());
        return batchSubjectRepository.save(bs);
    }

    /** Registers a student directly, with an active flag and unique code. */
    public Student student(String fullName) {
        Student student = new Student();
        student.setFullName(fullName + " " + unique());
        student.setPhone("9000000001");
        student.setStudentCode("OMS-2026-"
                + String.format("%05d", studentRepository.nextStudentCodeValue()));
        student.setActive(true);
        student.setAdmissionDate(DAY);
        return studentRepository.save(student);
    }

    /** Creates a TEACHER-role login account (active) with a unique email. */
    public UserAccount teacherUser(String emailPrefix) {
        return user(emailPrefix, UserAccount.Role.TEACHER);
    }

    /** Creates an ADMIN-role login account (active) with a unique email. */
    public UserAccount adminUser(String emailPrefix) {
        return user(emailPrefix, UserAccount.Role.ADMIN);
    }

    /** Creates a teacher profile that is not linked to any login account. */
    public Teacher teacher(String fullName) {
        Teacher teacher = new Teacher();
        teacher.setFullName(fullName + " " + unique());
        teacher.setPhone("8000000001");
        return teacherRepository.save(teacher);
    }

    /** Marks a batch inactive directly (bypassing the service layer). */
    public Batch deactivate(Batch batch) {
        Batch managed = batchRepository.findById(batch.getId()).orElseThrow();
        managed.setStatus(Batch.Status.INACTIVE);
        return batchRepository.save(managed);
    }

    /** Deactivates a student directly (bypassing the service layer). */
    public Student deactivate(Student student) {
        Student managed = studentRepository.findById(student.getId()).orElseThrow();
        managed.setActive(false);
        return studentRepository.save(managed);
    }

    /** Deactivates a course directly (bypassing the service layer). */
    public Course deactivate(Course course) {
        Course managed = courseRepository.findById(course.getId()).orElseThrow();
        managed.setStatus(Course.Status.INACTIVE);
        return courseRepository.save(managed);
    }

    /** Saves a direct ACTIVE enrollment row (bypassing the service layer). */
    public Enrollment activeEnrollment(Student student, Batch batch) {
        return activeEnrollment(student, batch, DAY);
    }

    /** Saves a direct ACTIVE enrollment row starting on a specific date. */
    public Enrollment activeEnrollment(Student student, Batch batch, LocalDate enrollmentDate) {
        Enrollment enrollment = new Enrollment();
        enrollment.setStudent(studentRepository.findById(student.getId()).orElseThrow());
        enrollment.setBatch(batchRepository.findById(batch.getId()).orElseThrow());
        enrollment.setCourse(courseRepository.findById(batch.getCourse().getId()).orElseThrow());
        enrollment.setEnrollmentDate(enrollmentDate);
        enrollment.setStatus(Enrollment.Status.ACTIVE);
        return enrollmentRepository.save(enrollment);
    }

    /** Creates a teacher profile linked to a login account. */
    public Teacher teacherLinkedTo(UserAccount user, String fullName) {
        Teacher teacher = teacher(fullName);
        Teacher managed = teacherRepository.findById(teacher.getId()).orElseThrow();
        managed.setLinkedUser(userAccountRepository.findById(user.getId()).orElseThrow());
        return teacherRepository.save(managed);
    }

    /** Assigns a teacher to a batch-subject assignment. */
    public BatchSubject assignTeacher(BatchSubject batchSubject, Teacher teacher) {
        BatchSubject managed = batchSubjectRepository.findById(batchSubject.getId()).orElseThrow();
        managed.setTeacher(teacherRepository.findById(teacher.getId()).orElseThrow());
        return batchSubjectRepository.save(managed);
    }

    /** A password accepted by the forms (at least 12 characters). */
    public String safePassword() {
        return "Password#2026#Strong";
    }

    private UserAccount user(String emailPrefix, UserAccount.Role role) {
        UserAccount user = new UserAccount();
        user.setFullName(role.name() + " " + unique());
        user.setEmail(emailPrefix + "." + unique() + "@omsai.local");
        user.setRole(role);
        user.setActive(true);
        user.setPasswordHash(passwordEncoder.encode(safePassword()));
        return userAccountRepository.save(user);
    }

    /** A unique email fragment so repeated runs never collide. */
    private String unique() {
        return String.valueOf(System.nanoTime() % 1_000_000_000L);
    }
}
