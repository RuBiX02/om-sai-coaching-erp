package com.omsai.coaching.dashboard;

import com.omsai.coaching.batch.BatchRepository;
import com.omsai.coaching.course.CourseRepository;
import com.omsai.coaching.course.Course.Status;
import com.omsai.coaching.enrollment.Enrollment;
import com.omsai.coaching.enrollment.EnrollmentRepository;
import com.omsai.coaching.student.StudentRepository;
import com.omsai.coaching.teacher.TeacherRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Figures for the administrator dashboard. All values come from the
 * database - nothing is hardcoded.
 */
@Service
public class DashboardService {

    private final StudentRepository studentRepository;
    private final CourseRepository courseRepository;
    private final BatchRepository batchRepository;
    private final TeacherRepository teacherRepository;
    private final EnrollmentRepository enrollmentRepository;

    public DashboardService(StudentRepository studentRepository,
                            CourseRepository courseRepository,
                            BatchRepository batchRepository,
                            TeacherRepository teacherRepository,
                            EnrollmentRepository enrollmentRepository) {
        this.studentRepository = studentRepository;
        this.courseRepository = courseRepository;
        this.batchRepository = batchRepository;
        this.teacherRepository = teacherRepository;
        this.enrollmentRepository = enrollmentRepository;
    }

    public record Stats(long activeStudents, long activeCourses, long activeBatches, long activeTeachers) {
    }

    @Transactional(readOnly = true)
    public Stats stats() {
        return new Stats(
                studentRepository.countByActiveTrue(),
                courseRepository.countByStatus(Status.ACTIVE),
                batchRepository.countByStatus(com.omsai.coaching.batch.Batch.Status.ACTIVE),
                teacherRepository.countByActiveTrue());
    }

    /** Five most recent enrollments for the dashboard table. */
    @Transactional(readOnly = true)
    public List<Enrollment> recentEnrollments() {
        return enrollmentRepository.findRecent(PageRequest.of(0, 5));
    }
}
