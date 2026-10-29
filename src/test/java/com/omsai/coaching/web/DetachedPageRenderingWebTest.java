package com.omsai.coaching.web;

import com.omsai.coaching.batch.Batch;
import com.omsai.coaching.batch.BatchSubject;
import com.omsai.coaching.batch.CourseSubject;
import com.omsai.coaching.course.Course;
import com.omsai.coaching.notice.Announcement;
import com.omsai.coaching.notice.NoticeService;
import com.omsai.coaching.security.AppUserPrincipal;
import com.omsai.coaching.security.UserAccount;
import com.omsai.coaching.student.Quiz;
import com.omsai.coaching.student.QuizAuthoringService;
import com.omsai.coaching.subject.Subject;
import com.omsai.coaching.support.Fixtures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The pages must render after their service transaction has closed.
 *
 * <p>{@code spring.jpa.open-in-view} is off, so a template that walks a
 * lazy relation which was not fetched by the service fails with
 * {@code LazyInitializationException} - exactly what happens in
 * production, but NOT inside a {@code @Transactional} web test, whose
 * session stays open for the whole request. This class therefore runs
 * with {@link Propagation#NOT_SUPPORTED} and deletes the rows it commits
 * again; it guards the notice list, the student notice board and the
 * staff quiz pages against detached-lazy regressions.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class DetachedPageRenderingWebTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private Fixtures fixtures;

    @Autowired
    private NoticeService noticeService;

    @Autowired
    private QuizAuthoringService quizAuthoringService;

    @Autowired
    private JdbcTemplate jdbc;

    private UserAccount admin;
    private Course course;
    private Batch batch;
    private Subject subject;
    private CourseSubject courseSubject;
    private BatchSubject assignment;
    private Quiz quiz;
    private Announcement notice;

    @BeforeEach
    void createCommittedData() {
        admin = fixtures.adminUser("detached.admin");
        course = fixtures.course("Detached Course");
        batch = fixtures.batch(course, "Morning");
        subject = fixtures.subject("Detached Subject");
        courseSubject = fixtures.assign(course, subject);
        assignment = fixtures.assignCourseSubject(batch, courseSubject);
        quiz = quizAuthoringService.create(assignment.getId(), "Detached quiz",
                "A quiz whose page must render after the session closes.", 10, admin);
        notice = noticeService.publish("Detached notice",
                "The publisher is a lazy relation of the announcement.",
                Announcement.Audience.ALL.name(), null, admin);
    }

    @AfterEach
    void deleteCommittedData() {
        if (notice != null) {
            jdbc.update("delete from announcement_reads where announcement_id = ?",
                    notice.getId());
            jdbc.update("delete from announcements where id = ?", notice.getId());
        }
        if (quiz != null) {
            jdbc.update("delete from quiz_questions where quiz_id = ?", quiz.getId());
            jdbc.update("delete from quiz_attempts where quiz_id = ?", quiz.getId());
            jdbc.update("delete from quizzes where id = ?", quiz.getId());
        }
        if (assignment != null) {
            jdbc.update("delete from batch_subjects where id = ?", assignment.getId());
        }
        if (courseSubject != null) {
            jdbc.update("delete from course_subjects where id = ?", courseSubject.getId());
        }
        if (batch != null) {
            jdbc.update("delete from batches where id = ?", batch.getId());
        }
        if (course != null) {
            jdbc.update("delete from courses where id = ?", course.getId());
        }
        if (subject != null) {
            jdbc.update("delete from subjects where id = ?", subject.getId());
        }
        if (admin != null) {
            jdbc.update("delete from user_accounts where id = ?", admin.getId());
        }
    }

    @Test
    void theOfficeNoticeListRendersWithItsPublisher() throws Exception {
        mockMvc.perform(get("/notices").with(user(AppUserPrincipal.from(admin))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Detached notice")))
                .andExpect(content().string(containsString(admin.getFullName())));
    }

    @Test
    void theQuizQuestionPageRendersWithItsCourse() throws Exception {
        mockMvc.perform(get("/quizzes/" + quiz.getId())
                        .with(user(AppUserPrincipal.from(admin))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Detached quiz")))
                .andExpect(content().string(containsString(course.getName())))
                .andExpect(content().string(containsString(batch.getName())));
    }
}
