package com.omsai.coaching.subject;

import com.omsai.coaching.batch.CourseSubject;
import com.omsai.coaching.batch.CourseSubjectRepository;
import com.omsai.coaching.common.BusinessRuleException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * Subject management business rules.
 *
 * <p>A subject that is not used by any course is disposable and may be
 * deleted. A subject that is already part of a course carries history
 * - the course assignments, the batches taught from them and the
 * classes that took place - so it is deactivated instead of deleted.
 * Deactivating keeps every relationship and only takes the subject out
 * of the pickers, so it cannot be assigned to a new course any more.</p>
 */
@Service
public class SubjectService {

    private final SubjectRepository subjectRepository;
    private final CourseSubjectRepository courseSubjectRepository;

    public SubjectService(SubjectRepository subjectRepository,
                          CourseSubjectRepository courseSubjectRepository) {
        this.subjectRepository = subjectRepository;
        this.courseSubjectRepository = courseSubjectRepository;
    }

    @Transactional(readOnly = true)
    public Page<Subject> search(String q, Pageable pageable) {
        String query = (q == null || q.isBlank()) ? "" : q.trim().toLowerCase();
        return subjectRepository.search(query, pageable);
    }

    @Transactional(readOnly = true)
    public Subject get(Long id) {
        return subjectRepository.findById(id)
                .orElseThrow(() -> new BusinessRuleException("Subject not found."));
    }

    @Transactional
    public Subject create(SubjectForm form) {
        ensureNameFree(form.getName(), null);
        Subject subject = new Subject();
        subject.setName(form.getName().trim());
        subject.setDescription(clean(form.getDescription()));
        return subjectRepository.save(subject);
    }

    @Transactional
    public Subject update(Long id, SubjectForm form) {
        Subject subject = get(id);
        subjectRepository.findByNameIgnoreCase(form.getName().trim()).ifPresent(existing -> {
            if (!existing.getId().equals(id)) {
                throw new BusinessRuleException("Another subject named \"" + form.getName().trim() + "\" already exists.");
            }
        });
        subject.setName(form.getName().trim());
        subject.setDescription(clean(form.getDescription()));
        return subjectRepository.save(subject);
    }

    /**
     * Deletes a subject that was never used.
     *
     * <p>Only a subject without any course assignment may go: as soon
     * as one course includes it, the assignment, the batches taught
     * from it and their classes are history that must survive. The
     * subject row is locked first, so a course assignment that is
     * created at the same moment cannot slip in behind the check; the
     * {@code ON DELETE RESTRICT} foreign key of {@code course_subjects}
     * is the final guard.</p>
     */
    @Transactional
    public void delete(Long id) {
        Subject subject = subjectRepository.getForUpdate(id)
                .orElseThrow(() -> new BusinessRuleException("Subject not found."));
        long courses = courseSubjectRepository.countForSubject(subject.getId());
        if (courses > 0) {
            throw new BusinessRuleException(
                    "This subject is part of " + courses + " course"
                            + (courses == 1 ? "" : "s")
                            + ", so it cannot be deleted without losing the teaching history of those courses. "
                            + "Deactivate it instead - it will then no longer be offered for new courses, "
                            + "while the existing courses, batches and classes stay exactly as they are.");
        }
        subjectRepository.delete(subject);
    }

    /**
     * Deactivates or reactivates a subject.
     *
     * <p>Deactivating keeps every course relationship and every
     * historical record; it only takes the subject out of the pickers,
     * so no new course can be assigned it. Reactivating puts it back
     * without recreating a single relationship.</p>
     */
    @Transactional
    public Subject setActive(Long id, boolean active) {
        Subject subject = subjectRepository.getForUpdate(id)
                .orElseThrow(() -> new BusinessRuleException("Subject not found."));
        if (subject.isActive() == active) {
            throw new BusinessRuleException(active
                    ? "This subject is already active."
                    : "This subject is already deactivated.");
        }
        subject.setActive(active);
        return subjectRepository.save(subject);
    }

    /** Courses that currently include this subject (with counts kept simple). */
    @Transactional(readOnly = true)
    public List<CourseSubject> coursesOf(Subject subject) {
        return courseSubjectRepository.findForSubject(subject.getId());
    }

    @Transactional(readOnly = true)
    public long batchAssignmentCount(Long subjectId) {
        return courseSubjectRepository.countAssignmentsForSubject(subjectId);
    }

    /** How many courses include this subject (used to decide delete vs deactivate). */
    @Transactional(readOnly = true)
    public long courseCount(Long subjectId) {
        return courseSubjectRepository.countForSubject(subjectId);
    }

    /** True when the subject is still taught (and may be assigned to a course). */
    @Transactional(readOnly = true)
    public boolean isActive(Long subjectId) {
        return subjectRepository.findById(subjectId)
                .map(Subject::isActive)
                .orElse(false);
    }

    private void ensureNameFree(String name, Long excludeId) {
        subjectRepository.findByNameIgnoreCase(name.trim()).ifPresent(existing -> {
            if (excludeId == null || !existing.getId().equals(excludeId)) {
                throw new BusinessRuleException("A subject named \"" + name.trim() + "\" already exists.");
            }
        });
    }

    private static String clean(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
