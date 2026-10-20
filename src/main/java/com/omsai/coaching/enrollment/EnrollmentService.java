package com.omsai.coaching.enrollment;

import com.omsai.coaching.batch.Batch;
import com.omsai.coaching.batch.BatchRepository;
import com.omsai.coaching.common.BusinessRuleException;
import com.omsai.coaching.course.Course;
import com.omsai.coaching.student.Student;
import com.omsai.coaching.student.StudentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Enrollment business rules. Enrollment records are never deleted:
 * transfers close the previous record and create a new one inside a
 * single transaction.
 */
@Service
public class EnrollmentService {

    private final EnrollmentRepository enrollmentRepository;
    private final StudentRepository studentRepository;
    private final BatchRepository batchRepository;

    public EnrollmentService(EnrollmentRepository enrollmentRepository,
                             StudentRepository studentRepository,
                             BatchRepository batchRepository) {
        this.enrollmentRepository = enrollmentRepository;
        this.studentRepository = studentRepository;
        this.batchRepository = batchRepository;
    }

    /**
     * Enrolls a student in a batch. Rules:
     * - the student must be active;
     * - the batch and its course must be active;
     * - no other ACTIVE enrollment may exist for the same student+batch;
     * - no other ACTIVE enrollment may exist for the same student+course
     *   (use a transfer instead).
     */
    @Transactional
    public Enrollment enroll(EnrollForm form) {
        Student student = studentRepository.findById(form.getStudentId())
                .orElseThrow(() -> new BusinessRuleException("Student not found."));
        if (!student.isActive()) {
            throw new BusinessRuleException(
                    "Student " + student.getFullName() + " is inactive and cannot be enrolled.");
        }
        Batch batch = batchRepository.findById(form.getBatchId())
                .orElseThrow(() -> new BusinessRuleException("Batch not found."));
        if (!batch.isActive()) {
            throw new BusinessRuleException("Batch \"" + batch.getName() + "\" is inactive.");
        }
        Course course = batch.getCourse();
        if (!course.isActive()) {
            throw new BusinessRuleException(
                    "Course \"" + course.getName() + "\" is inactive, so its batches cannot accept enrollments.");
        }
        if (enrollmentRepository.existsByStudentIdAndBatchIdAndStatus(
                student.getId(), batch.getId(), Enrollment.Status.ACTIVE)) {
            throw new BusinessRuleException("This student already has an active enrollment in that batch.");
        }
        if (enrollmentRepository.existsByStudentIdAndCourseIdAndStatus(
                student.getId(), course.getId(), Enrollment.Status.ACTIVE)) {
            throw new BusinessRuleException(
                    "This student already has an active enrollment in another batch of \""
                            + course.getName() + "\". Use a transfer to change batches.");
        }
        Enrollment enrollment = new Enrollment();
        enrollment.setStudent(student);
        enrollment.setBatch(batch);
        enrollment.setCourse(course);
        enrollment.setEnrollmentDate(form.getEnrollmentDate());
        enrollment.setStatus(Enrollment.Status.ACTIVE);
        return enrollmentRepository.save(enrollment);
    }

    /** Marks an ACTIVE enrollment as COMPLETED. */
    @Transactional
    public void complete(Long enrollmentId) {
        Enrollment enrollment = getActive(enrollmentId);
        enrollment.setStatus(Enrollment.Status.COMPLETED);
    }

    /** Marks an ACTIVE enrollment as WITHDRAWN. */
    @Transactional
    public void withdraw(Long enrollmentId) {
        Enrollment enrollment = getActive(enrollmentId);
        enrollment.setStatus(Enrollment.Status.WITHDRAWN);
    }

    private Enrollment getActive(Long enrollmentId) {
        Enrollment enrollment = enrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> new BusinessRuleException("Enrollment not found."));
        if (enrollment.getStatus() != Enrollment.Status.ACTIVE) {
            throw new BusinessRuleException(
                    "Only ACTIVE enrollments can be updated (this one is " + enrollment.getStatus() + ").");
        }
        return enrollment;
    }

    /**
     * Transfers a student to another batch of the same course. The whole
     * operation runs in one transaction: every ACTIVE enrollment of the
     * student in the target course is closed as COMPLETED and a new
     * ACTIVE enrollment is created in the target batch. Previous records
     * are never deleted, so the enrollment history is preserved.
     */
    @Transactional
    public Enrollment transfer(TransferForm form) {
        Student student = studentRepository.findById(form.getStudentId())
                .orElseThrow(() -> new BusinessRuleException("Student not found."));
        if (!student.isActive()) {
            throw new BusinessRuleException(
                    "Student " + student.getFullName() + " is inactive and cannot be transferred.");
        }
        Batch targetBatch = batchRepository.findById(form.getTargetBatchId())
                .orElseThrow(() -> new BusinessRuleException("Target batch not found."));
        if (!targetBatch.isActive()) {
            throw new BusinessRuleException("Target batch \"" + targetBatch.getName() + "\" is inactive.");
        }
        Course targetCourse = targetBatch.getCourse();
        if (!targetCourse.isActive()) {
            throw new BusinessRuleException(
                    "Course \"" + targetCourse.getName() + "\" is inactive and cannot accept enrollments.");
        }

        List<Enrollment> activeInCourse = enrollmentRepository.findActiveInCourse(
                student.getId(), targetCourse.getId(), Enrollment.Status.ACTIVE);
        if (activeInCourse.isEmpty()) {
            throw new BusinessRuleException(
                    "This student has no active enrollment in a batch of \"" + targetCourse.getName()
                            + "\". Use a new enrollment instead of a transfer.");
        }
        boolean activeInTargetBatch = activeInCourse.stream()
                .anyMatch(e -> e.getBatch().getId().equals(targetBatch.getId()));
        if (activeInTargetBatch) {
            throw new BusinessRuleException("The student is already active in that batch.");
        }

        for (Enrollment previous : activeInCourse) {
            previous.setStatus(Enrollment.Status.COMPLETED);
        }
        // Persist the closures BEFORE inserting the new active enrollment,
        // otherwise the insert would violate the database constraint
        // "one ACTIVE enrollment per student per course".
        enrollmentRepository.saveAll(activeInCourse);
        enrollmentRepository.flush();
        Enrollment enrollment = new Enrollment();
        enrollment.setStudent(student);
        enrollment.setBatch(targetBatch);
        enrollment.setCourse(targetCourse);
        enrollment.setEnrollmentDate(form.getTransferDate());
        enrollment.setStatus(Enrollment.Status.ACTIVE);
        return enrollmentRepository.save(enrollment);
    }
}
