package com.omsai.coaching.dashboard;

import com.omsai.coaching.course.Course;
import com.omsai.coaching.enrollment.Enrollment;
import com.omsai.coaching.support.Fixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Dashboard figures must come from the database and stay consistent with
 * the created fixture data.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DashboardServiceTest {

    @Autowired
    private DashboardService dashboardService;

    @Autowired
    private Fixtures fixtures;

    @Test
    void statsCountTheFixedRowData() {
        Course course = fixtures.course("Dashboard Course");
        var batch = fixtures.batch(course, "Dashboard Batch");
        var student = fixtures.student("Dashboard Student");
        fixtures.assignCourseSubject(batch, fixtures.assign(course, fixtures.subject("Dashboard Subject")));

        var stats = dashboardService.stats();

        assertThat(stats.activeStudents()).isGreaterThanOrEqualTo(1);
        assertThat(stats.activeCourses()).isGreaterThanOrEqualTo(1);
        assertThat(stats.activeBatches()).isGreaterThanOrEqualTo(1);
        assertThat(stats.activeTeachers()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void recentEnrollmentsListsAtMostFive() {
        Course course = fixtures.course("Recent Course");
        var batch = fixtures.batch(course, "Recent Batch");
        var student = fixtures.student("Recent Student");
        // enroll three times (with withdrawals) to create history rows
        Enrollment enrollment = fixtures.activeEnrollment(student, batch);
        enrollment.setStatus(Enrollment.Status.WITHDRAWN);
        fixtures.activeEnrollment(fixtures.student("Recent Student 2"), batch);
        fixtures.activeEnrollment(fixtures.student("Recent Student 3"), batch);

        var recent = dashboardService.recentEnrollments();

        assertThat(recent).hasSizeLessThanOrEqualTo(5);
        assertThat(recent).hasSizeGreaterThanOrEqualTo(2);
    }
}
