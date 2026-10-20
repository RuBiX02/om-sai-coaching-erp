package com.omsai.coaching.enrollment;

import com.omsai.coaching.batch.Batch;
import com.omsai.coaching.course.Course;
import com.omsai.coaching.student.Student;

/**
 * Notified inside the transfer transaction after the new enrollment has
 * been created. Observers may attach existing records (for example a
 * fee agreement) to the new enrollment; they must never create a second
 * financial liability for the same course and academic year.
 */
public interface EnrollmentTransferObserver {

    /**
     * @param student      the transferred student
     * @param course       the course the student was transferred within
     * @param targetBatch  the new batch
     * @param newEnrollment the ACTIVE enrollment created by the transfer
     */
    void onTransfer(Student student, Course course, Batch targetBatch, Enrollment newEnrollment);
}
