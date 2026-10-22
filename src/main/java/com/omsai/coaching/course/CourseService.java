package com.omsai.coaching.course;

import com.omsai.coaching.batch.Batch;
import com.omsai.coaching.batch.BatchRepository;
import com.omsai.coaching.batch.CourseSubject;
import com.omsai.coaching.batch.CourseSubjectRepository;
import com.omsai.coaching.common.BusinessRuleException;
import com.omsai.coaching.subject.Subject;
import com.omsai.coaching.subject.SubjectRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * Course management business rules.
 */
@Service
public class CourseService {

    private final CourseRepository courseRepository;
    private final SubjectRepository subjectRepository;
    private final CourseSubjectRepository courseSubjectRepository;
    private final BatchRepository batchRepository;

    public CourseService(CourseRepository courseRepository,
                         SubjectRepository subjectRepository,
                         CourseSubjectRepository courseSubjectRepository,
                         BatchRepository batchRepository) {
        this.courseRepository = courseRepository;
        this.subjectRepository = subjectRepository;
        this.courseSubjectRepository = courseSubjectRepository;
        this.batchRepository = batchRepository;
    }

    @Transactional(readOnly = true)
    public Page<Course> search(String q, Course.Status status, Pageable pageable) {
        // Empty string (never null) keeps parameter binding unambiguous.
        String query = (q == null || q.isBlank()) ? "" : q.trim().toLowerCase();
        return courseRepository.search(query, status, pageable);
    }

    @Transactional(readOnly = true)
    public Course get(Long id) {
        return courseRepository.findById(id)
                .orElseThrow(() -> new BusinessRuleException("Course not found."));
    }

    @Transactional
    public Course create(CourseForm form) {
        ensureNameFree(form.getName(), null);
        Course course = new Course();
        applyForm(course, form);
        return courseRepository.save(course);
    }

    @Transactional
    public Course update(Long id, CourseForm form) {
        Course course = get(id);
        ensureNameFree(form.getName(), id);
        applyForm(course, form);
        return courseRepository.save(course);
    }

    /**
     * Activates or deactivates a course. Deactivation keeps all existing
     * batches, assignments and enrollment history intact.
     */
    @Transactional
    public Course setStatus(Long id, boolean active) {
        Course course = get(id);
        course.setStatus(active ? Course.Status.ACTIVE : Course.Status.INACTIVE);
        return courseRepository.save(course);
    }

    @Transactional(readOnly = true)
    public List<CourseSubject> assignedSubjects(Course course) {
        return courseSubjectRepository.findForCourse(course.getId());
    }

    @Transactional(readOnly = true)
    public List<Subject> assignableSubjects(Course course) {
        return courseSubjectRepository.findUnassignedSubjects(course);
    }

    /** Assigns a subject to a course. Duplicate pairs are rejected. */
    @Transactional
    public void assignSubject(Long courseId, Long subjectId) {
        Course course = get(courseId);
        if (!course.isActive()) {
            throw new BusinessRuleException("Subjects can only be assigned to an active course.");
        }
        Subject subject = subjectRepository.findById(subjectId)
                .orElseThrow(() -> new BusinessRuleException("Subject not found."));
        if (!subject.isActive()) {
            throw new BusinessRuleException(
                    "Subject \"" + subject.getName() + "\" is deactivated and cannot be assigned to a course. "
                            + "Activate it first if it is taught again.");
        }
        if (courseSubjectRepository.existsByCourseIdAndSubjectId(courseId, subjectId)) {
            throw new BusinessRuleException(
                    "Subject \"" + subject.getName() + "\" is already assigned to this course.");
        }
        CourseSubject cs = new CourseSubject();
        cs.setCourse(course);
        cs.setSubject(subject);
        courseSubjectRepository.save(cs);
    }

    /**
     * Removes a subject from a course, but only when no batch subject
     * assignment (current teaching assignment) depends on it.
     */
    @Transactional
    public void removeSubject(Long courseId, Long courseSubjectId) {
        CourseSubject cs = courseSubjectRepository.findById(courseSubjectId)
                .orElseThrow(() -> new BusinessRuleException("Subject assignment not found."));
        if (!cs.getCourse().getId().equals(courseId)) {
            throw new BusinessRuleException("That subject assignment does not belong to this course.");
        }
        if (courseSubjectRepository.countAssignments(courseSubjectId) > 0) {
            throw new BusinessRuleException(
                    "This subject is taught in one or more batches of this course, so it cannot be removed "
                            + "without losing teaching history. Remove the batch assignment first.");
        }
        courseSubjectRepository.delete(cs);
    }

    @Transactional(readOnly = true)
    public List<Batch> batchesOf(Long courseId) {
        return batchRepository.findForCourse(courseId);
    }

    @Transactional(readOnly = true)
    public List<Course> activeCourses() {
        return courseRepository.findByStatusOrderByName(Course.Status.ACTIVE);
    }

    private void applyForm(Course course, CourseForm form) {
        course.setName(form.getName().trim());
        course.setCategory(form.getCategory().trim());
        course.setDescription(clean(form.getDescription()));
        course.setDurationMonths(form.getDurationMonths());
    }

    private void ensureNameFree(String name, Long excludeId) {
        courseRepository.findByNameIgnoreCase(name.trim()).ifPresent(existing -> {
            if (excludeId == null || !existing.getId().equals(excludeId)) {
                throw new BusinessRuleException("A course with the name \"" + name.trim() + "\" already exists.");
            }
        });
    }

    private static String normalize(String q) {
        if (q == null || q.isBlank()) {
            return "";
        }
        return q.trim().toLowerCase();
    }

    private static String clean(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
