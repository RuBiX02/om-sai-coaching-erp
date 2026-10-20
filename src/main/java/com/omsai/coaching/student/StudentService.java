package com.omsai.coaching.student;

import com.omsai.coaching.common.BusinessRuleException;
import com.omsai.coaching.enrollment.Enrollment;
import com.omsai.coaching.enrollment.EnrollmentRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.Year;
import java.util.List;

/**
 * Student management business rules, including unique student code
 * generation.
 */
@Service
public class StudentService {

    private final StudentRepository studentRepository;
    private final EnrollmentRepository enrollmentRepository;

    public StudentService(StudentRepository studentRepository, EnrollmentRepository enrollmentRepository) {
        this.studentRepository = studentRepository;
        this.enrollmentRepository = enrollmentRepository;
    }

    @Transactional(readOnly = true)
    public Page<Student> search(String q, Boolean active, Pageable pageable) {
        String query = (q == null || q.isBlank()) ? "" : q.trim().toLowerCase();
        return studentRepository.search(query, active, pageable);
    }

    @Transactional(readOnly = true)
    public Student get(Long id) {
        return studentRepository.findById(id)
                .orElseThrow(() -> new BusinessRuleException("Student not found."));
    }

    /** Registers a student and generates a unique student code. */
    @Transactional
    public Student create(StudentForm form) {
        Student student = new Student();
        applyForm(student, form);
        student.setStudentCode(generateCode());
        return studentRepository.save(student);
    }

    @Transactional
    public Student update(Long id, StudentForm form) {
        Student student = get(id);
        applyForm(student, form);
        return studentRepository.save(student);
    }

    @Transactional
    public Student setStatus(Long id, boolean active) {
        Student student = get(id);
        student.setActive(active);
        return studentRepository.save(student);
    }

    @Transactional(readOnly = true)
    public List<Enrollment> enrollmentsOf(Student student) {
        return enrollmentRepository.findForStudent(student.getId());
    }

    // ----- helpers ----------------------------------------------------------

    private void applyForm(Student student, StudentForm form) {
        student.setFullName(form.getFullName().trim());
        student.setPhone(form.getPhone().trim());
        student.setParentName(clean(form.getParentName()));
        student.setParentPhone(clean(form.getParentPhone()));
        student.setAddress(clean(form.getAddress()));
        student.setAdmissionDate(form.getAdmissionDate());
    }

    /**
     * Codes look like OMS-2026-00001. The value comes from a PostgreSQL
     * sequence so it is unique even under concurrent registrations; the
     * uniqueness check below is a defensive second layer.
     */
    private String generateCode() {
        for (int attempt = 0; attempt < 5; attempt++) {
            long next = studentRepository.nextStudentCodeValue();
            String candidate = "OMS-" + Year.now() + "-" + String.format("%05d", next);
            if (!studentRepository.existsByStudentCodeIgnoreCase(candidate)) {
                return candidate;
            }
        }
        throw new BusinessRuleException("Could not generate a unique student code. Please try again.");
    }

    private static String clean(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
