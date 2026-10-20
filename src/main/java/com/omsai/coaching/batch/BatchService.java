package com.omsai.coaching.batch;

import com.omsai.coaching.common.BusinessRuleException;
import com.omsai.coaching.course.Course;
import com.omsai.coaching.course.CourseRepository;
import com.omsai.coaching.enrollment.Enrollment;
import com.omsai.coaching.enrollment.EnrollmentRepository;
import com.omsai.coaching.teacher.Teacher;
import com.omsai.coaching.teacher.TeacherRepository;
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

    public BatchService(BatchRepository batchRepository,
                        CourseRepository courseRepository,
                        CourseSubjectRepository courseSubjectRepository,
                        BatchSubjectRepository batchSubjectRepository,
                        TeacherRepository teacherRepository,
                        EnrollmentRepository enrollmentRepository) {
        this.batchRepository = batchRepository;
        this.courseRepository = courseRepository;
        this.courseSubjectRepository = courseSubjectRepository;
        this.batchSubjectRepository = batchSubjectRepository;
        this.teacherRepository = teacherRepository;
        this.enrollmentRepository = enrollmentRepository;
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
                .filter(cs -> !batchSubjectRepository.existsByBatchIdAndCourseSubjectId(batch.getId(), cs.getId()))
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

    /** Adds a course subject to the batch; it must belong to the batch's course. */
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
        if (batchSubjectRepository.existsByBatchIdAndCourseSubjectId(batchId, courseSubjectId)) {
            throw new BusinessRuleException("That subject is already assigned to this batch.");
        }
        BatchSubject bs = new BatchSubject();
        bs.setBatch(batch);
        bs.setCourseSubject(cs);
        batchSubjectRepository.save(bs);
    }

    @Transactional
    public void removeSubject(Long batchSubjectId) {
        batchSubjectRepository.findById(batchSubjectId)
                .orElseThrow(() -> new BusinessRuleException("Batch subject assignment not found."));
        batchSubjectRepository.deleteById(batchSubjectId);
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
