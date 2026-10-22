package com.omsai.coaching.batch;

import com.omsai.coaching.attendance.ClassSessionRepository;
import com.omsai.coaching.common.BusinessRuleException;
import com.omsai.coaching.course.Course;
import com.omsai.coaching.course.CourseRepository;
import com.omsai.coaching.enrollment.Enrollment;
import com.omsai.coaching.enrollment.EnrollmentRepository;
import com.omsai.coaching.examination.ExaminationRepository;
import com.omsai.coaching.teacher.Teacher;
import com.omsai.coaching.teacher.TeacherRepository;
import com.omsai.coaching.timetable.TimetableEntryRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Batch management business rules, including course-subject and teacher
 * assignments.
 */
@Service
public class BatchService {

    private final BatchRepository batchRepository;
    private final CourseRepository courseRepository;
    private final CourseSubjectRepository courseSubjectRepository;
    private final BatchSubjectRepository batchSubjectRepository;
    private final TeacherRepository teacherRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final ClassSessionRepository classSessionRepository;
    private final TimetableEntryRepository timetableEntryRepository;
    private final ExaminationRepository examinationRepository;

    public BatchService(BatchRepository batchRepository,
                        CourseRepository courseRepository,
                        CourseSubjectRepository courseSubjectRepository,
                        BatchSubjectRepository batchSubjectRepository,
                        TeacherRepository teacherRepository,
                        EnrollmentRepository enrollmentRepository,
                        ClassSessionRepository classSessionRepository,
                        TimetableEntryRepository timetableEntryRepository,
                        ExaminationRepository examinationRepository) {
        this.batchRepository = batchRepository;
        this.courseRepository = courseRepository;
        this.courseSubjectRepository = courseSubjectRepository;
        this.batchSubjectRepository = batchSubjectRepository;
        this.teacherRepository = teacherRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.classSessionRepository = classSessionRepository;
        this.timetableEntryRepository = timetableEntryRepository;
        this.examinationRepository = examinationRepository;
    }

    @Transactional(readOnly = true)
    public Page<Batch> search(String q, Long courseId, Batch.Status status, Pageable pageable) {
        String query = (q == null || q.isBlank()) ? "" : q.trim().toLowerCase();
        return batchRepository.search(query, courseId, status, pageable);
    }

    @Transactional(readOnly = true)
    public Batch get(Long id) {
        return batchRepository.findWithCourse(id)
                .orElseThrow(() -> new BusinessRuleException("Batch not found."));
    }

    @Transactional(readOnly = true)
    public List<Course> activeCourses() {
        return courseRepository.findByStatusOrderByName(Course.Status.ACTIVE);
    }

    /** Active batches of active courses, for enrollment selects. */
    @Transactional(readOnly = true)
    public List<Batch> activeBatches() {
        return batchRepository.findActiveBatches(Batch.Status.ACTIVE, Course.Status.ACTIVE);
    }

    @Transactional
    public Batch create(BatchForm form) {
        Course course = courseRepository.findById(form.getCourseId())
                .orElseThrow(() -> new BusinessRuleException("Selected course does not exist."));
        if (!course.isActive()) {
            throw new BusinessRuleException("Batches can only be created for an active course.");
        }
        validateDates(form);
        ensureNameFree(form, null);
        Batch batch = new Batch();
        batch.setCourse(course);
        batch.setName(form.getName().trim());
        batch.setAcademicYear(form.getAcademicYear().trim());
        batch.setStartDate(form.getStartDate());
        batch.setEndDate(form.getEndDate());
        return batchRepository.save(batch);
    }

    /**
     * Edits batch details. The course of an existing batch never changes,
     * because batch subjects, teaching assignments and enrollments all
     * depend on it.
     */
    @Transactional
    public Batch update(Long id, BatchForm form) {
        Batch batch = get(id);
        form.setCourseId(batch.getCourse().getId());
        validateDates(form);
        ensureNameFree(form, id);
        batch.setName(form.getName().trim());
        batch.setAcademicYear(form.getAcademicYear().trim());
        batch.setStartDate(form.getStartDate());
        batch.setEndDate(form.getEndDate());
        return batchRepository.save(batch);
    }

    /** Activates or deactivates a batch. History is always preserved. */
    @Transactional
    public Batch setStatus(Long id, boolean active) {
        Batch batch = get(id);
        batch.setStatus(active ? Batch.Status.ACTIVE : Batch.Status.INACTIVE);
        return batchRepository.save(batch);
    }

    // ----- batch subjects and teachers -------------------------------------

    @Transactional(readOnly = true)
    public List<CourseSubject> assignableCourseSubjects(Batch batch) {
        List<CourseSubject> all = courseSubjectRepository.findForCourse(batch.getCourse().getId());
        return all.stream()
                .filter(cs -> !batchSubjectRepository.existsActiveByBatchIdAndCourseSubjectId(batch.getId(), cs.getId()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<BatchSubject> subjectsOf(Batch batch) {
        return batchSubjectRepository.findForBatch(batch.getId());
    }

    @Transactional(readOnly = true)
    public List<BatchSubject> assignmentsOf(Teacher teacher) {
        return batchSubjectRepository.findForTeacher(teacher.getId());
    }

    @Transactional(readOnly = true)
    public List<BatchSubject> assignmentsForUser(Long userId) {
        return batchSubjectRepository.findForUser(userId);
    }

    @Transactional(readOnly = true)
    public List<Teacher> activeTeachers() {
        return teacherRepository.findActiveTeachers();
    }

    /**
     * Adds a course subject to the batch; it must belong to the batch's course.
     * When the subject was taught in this batch before and was only
     * deactivated, the existing assignment is reactivated instead of
     * creating a second one.
     */
    @Transactional
    public void addSubject(Long batchId, Long courseSubjectId) {
        Batch batch = get(batchId);
        CourseSubject cs = courseSubjectRepository.findById(courseSubjectId)
                .orElseThrow(() -> new BusinessRuleException("Course subject not found."));
        if (!cs.getCourse().getId().equals(batch.getCourse().getId())) {
            throw new BusinessRuleException(
                    "That subject belongs to course \"" + cs.getCourse().getName()
                            + "\" and cannot be assigned to a batch of \"" + batch.getCourse().getName() + "\".");
        }
        Optional<BatchSubject> existing = batchSubjectRepository
                .findByBatchIdAndCourseSubjectId(batchId, courseSubjectId);
        if (existing.isPresent()) {
            BatchSubject bs = existing.get();
            if (bs.isActive()) {
                throw new BusinessRuleException("That subject is already assigned to this batch.");
            }
            // Re-teaching a subject that was deactivated: wake the old
            // assignment up so its classes, attendance and results stay
            // attached to the same record.
            bs.setActive(true);
            batchSubjectRepository.save(bs);
            return;
        }
        BatchSubject bs = new BatchSubject();
        bs.setBatch(batch);
        bs.setCourseSubject(cs);
        bs.setActive(true);
        batchSubjectRepository.save(bs);
    }

    /**
     * Removes a subject assignment from a batch.
     *
     * <p>Only an assignment that has never been used can be deleted. As
     * soon as classes, a timetable schedule or examinations exist, the
     * assignment carries teaching history that must survive, and it is
     * rejected with an explanation instead of failing with a database
     * error. Such an assignment is deactivated instead - see
     * {@link #setSubjectActive(Long, boolean)}.</p>
     */
    @Transactional
    public void removeSubject(Long batchSubjectId) {
        BatchSubject bs = batchSubjectRepository.findById(batchSubjectId)
                .orElseThrow(() -> new BusinessRuleException("Batch subject assignment not found."));
        SubjectUsage usage = usageOf(batchSubjectId);
        if (!usage.isRemovable()) {
            throw new BusinessRuleException(
                    "This subject cannot be removed from the batch because "
                            + usage.describe()
                            + ". That teaching history must be preserved. Deactivate the subject instead to stop "
                            + "offering it for new classes.");
        }
        batchSubjectRepository.delete(bs);
    }

    /**
     * Deactivates or reactivates a subject assignment.
     *
     * <p>Deactivating keeps the assignment and everything that depends on
     * it (classes, attendance, timetable entries, examinations) but takes
     * it out of the pickers, so no new class can be scheduled for it. A
     * deactivated assignment can be reactivated at any time and keeps
     * its teacher and its history.</p>
     */
    @Transactional
    public BatchSubject setSubjectActive(Long batchSubjectId, boolean active) {
        BatchSubject bs = batchSubjectRepository.findById(batchSubjectId)
                .orElseThrow(() -> new BusinessRuleException("Batch subject assignment not found."));
        if (bs.isActive() == active) {
            throw new BusinessRuleException(active
                    ? "This subject is already being taught in this batch."
                    : "This subject is already deactivated.");
        }
        if (active) {
            Batch batch = bs.getBatch();
            if (!batch.isActive()) {
                throw new BusinessRuleException(
                        "Batch \"" + batch.getName() + "\" is inactive, so its subjects cannot be reactivated.");
            }
            if (!batch.getCourse().isActive()) {
                throw new BusinessRuleException(
                        "Course \"" + batch.getCourse().getName()
                                + "\" is inactive, so its subjects cannot be reactivated.");
            }
        }
        bs.setActive(active);
        return batchSubjectRepository.save(bs);
    }

    /**
     * How much history depends on one assignment. The counts decide
     * whether the assignment may still be deleted.
     */
    @Transactional(readOnly = true)
    public SubjectUsage usageOf(Long batchSubjectId) {
        long sessions = classSessionRepository.countByBatchSubjectId(batchSubjectId);
        long schedules = timetableEntryRepository.countByBatchSubjectId(batchSubjectId);
        long examinations = examinationRepository.countByBatchSubjectId(batchSubjectId);
        return new SubjectUsage(sessions, schedules, examinations);
    }

    /** Assigns a teacher (or clears the assignment when teacherId is null). */
    @Transactional
    public void assignTeacher(Long batchSubjectId, Long teacherId) {
        BatchSubject bs = batchSubjectRepository.findById(batchSubjectId)
                .orElseThrow(() -> new BusinessRuleException("Batch subject assignment not found."));
        if (teacherId == null) {
            bs.setTeacher(null);
            batchSubjectRepository.save(bs);
            return;
        }
        Teacher teacher = teacherRepository.findById(teacherId)
                .orElseThrow(() -> new BusinessRuleException("Teacher not found."));
        if (!teacher.isActive()) {
            throw new BusinessRuleException(
                    "Teacher \"" + teacher.getFullName() + "\" is inactive and cannot receive new assignments.");
        }
        bs.setTeacher(teacher);
        batchSubjectRepository.save(bs);
    }

    @Transactional(readOnly = true)
    public List<Enrollment> enrollmentsOf(Batch batch) {
        return enrollmentRepository.findForBatch(batch.getId());
    }

    // ----- helpers ----------------------------------------------------------

    /**
     * The teaching history that depends on one subject assignment.
     * An assignment with no history at all is disposable and may be
     * deleted; anything else has to be preserved.
     */
    public record SubjectUsage(long classSessions, long timetableEntries, long examinations) {

        /** True when nothing depends on the assignment, so it may still be deleted. */
        public boolean isRemovable() {
            return classSessions == 0 && timetableEntries == 0 && examinations == 0;
        }

        /** A readable list of what keeps the assignment alive. */
        public String describe() {
            List<String> parts = new java.util.ArrayList<>();
            if (classSessions > 0) {
                parts.add(plural(classSessions, "class session", "class sessions"));
            }
            if (timetableEntries > 0) {
                parts.add(plural(timetableEntries, "timetable entry", "timetable entries"));
            }
            if (examinations > 0) {
                parts.add(plural(examinations, "examination", "examinations"));
            }
            return String.join(", ", parts);
        }

        private static String plural(long count, String singular, String plural) {
            return count + " " + (count == 1 ? singular : plural);
        }
    }

    private void validateDates(BatchForm form) {
        if (form.getEndDate() != null && form.getStartDate() != null
                && form.getEndDate().isBefore(form.getStartDate())) {
            throw new BusinessRuleException("Batch end date cannot be before the start date.");
        }
    }

    private void ensureNameFree(BatchForm form, Long excludeId) {
        Optional<Batch> existing = batchRepository
                .findSameNameBatch(form.getCourseId(), form.getAcademicYear().trim(), form.getName().toLowerCase());
        existing.ifPresent(b -> {
            if (excludeId == null || !b.getId().equals(excludeId)) {
                throw new BusinessRuleException("This course already has a batch named \""
                        + form.getName().trim() + "\" for academic year " + form.getAcademicYear().trim() + ".");
            }
        });
    }
}
