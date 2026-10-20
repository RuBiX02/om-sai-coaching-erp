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

    /** Courses that currently include this subject (with counts kept simple). */
    @Transactional(readOnly = true)
    public List<CourseSubject> coursesOf(Subject subject) {
        return courseSubjectRepository.findForSubject(subject.getId());
    }

    @Transactional(readOnly = true)
    public long batchAssignmentCount(Long subjectId) {
        return courseSubjectRepository.countAssignmentsForSubject(subjectId);
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
