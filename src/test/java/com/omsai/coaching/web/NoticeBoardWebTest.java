package com.omsai.coaching.web;

import com.omsai.coaching.batch.Batch;
import com.omsai.coaching.course.Course;
import com.omsai.coaching.notice.AnnouncementRepository;
import com.omsai.coaching.notice.NoticeService;
import com.omsai.coaching.security.AppUserPrincipal;
import com.omsai.coaching.security.UserAccount;
import com.omsai.coaching.security.UserForm;
import com.omsai.coaching.security.UserService;
import com.omsai.coaching.student.Student;
import com.omsai.coaching.support.Fixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The notice board: the office publishes, the addressed students read,
 * the badge counts what is unread - and nothing else leaks across roles
 * or batches.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class NoticeBoardWebTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private Fixtures fixtures;

    @Autowired
    private UserService userService;

    @Autowired
    private NoticeService noticeService;

    @Autowired
    private AnnouncementRepository announcementRepository;

    private UserAccount admin;
    private Batch batch;
    private Student student;
    private UserAccount studentAccount;

    @BeforeEach
    void buildFixture() {
        admin = fixtures.adminUser("notice.admin");
        Course course = fixtures.course("Notice Course");
        batch = fixtures.batch(course, "Morning");
        student = fixtures.student("Notice Student");
        fixtures.activeEnrollment(student, batch);
        studentAccount = createStudentAccount(student, "notice.student");
    }

    @Test
    void anAdminPublishesANoticeAndTheStudentBoardShowsIt() throws Exception {
        publish("Exam form deadline", "Submit the CET form before Friday, 5 pm.", "ALL", null)
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/notices"));

        assertThat(announcementRepository.count()).isEqualTo(1);

        String board = mockMvc.perform(get("/student/notices").with(as(studentAccount)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(board).contains("Exam form deadline")
                .contains("Submit the CET form before Friday, 5 pm.")
                .contains("Everyone");
    }

    @Test
    void aBatchNoticeOnlyReachesThatBatchesStudents() throws Exception {
        publish("Room change", "Tomorrow's physics class moves to Room B-2.", "BATCH",
                batch.getId())
                .andExpect(status().is3xxRedirection());

        String board = mockMvc.perform(get("/student/notices").with(as(studentAccount)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(board).contains("Room change");

        Student outsider = fixtures.student("Outsider Student");
        UserAccount outsiderAccount = createStudentAccount(outsider, "notice.outsider");
        String outsiderBoard = mockMvc.perform(get("/student/notices").with(as(outsiderAccount)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(outsiderBoard).doesNotContain("Room change");
        assertThat(noticeService.unreadCount(List.of(), outsiderAccount.getId())).isZero();
    }

    @Test
    void visitingTheBoardMarksTheNoticesRead() throws Exception {
        publish("Fee deadline", "The second installment is due on the 1st.", "ALL", null);
        long noticeId = announcementRepository.findAll().get(0).getId();

        assertThat(noticeService.unreadIds(List.of(batch.getId()), studentAccount.getId()))
                .contains(noticeId);
        assertThat(noticeService.unreadCount(List.of(batch.getId()), studentAccount.getId()))
                .isEqualTo(1);

        mockMvc.perform(get("/student/notices").with(as(studentAccount)))
                .andExpect(status().isOk());

        assertThat(noticeService.unreadIds(List.of(batch.getId()), studentAccount.getId()))
                .isEmpty();
        assertThat(noticeService.unreadCount(List.of(batch.getId()), studentAccount.getId()))
                .isZero();
    }

    @Test
    void aWithdrawnNoticeDisappearsFromThePortalButStaysOnTheOfficeList() throws Exception {
        publish("Holiday notice", "The institute stays closed on Monday.", "ALL", null);
        long noticeId = announcementRepository.findAll().get(0).getId();

        mockMvc.perform(post("/notices/" + noticeId + "/withdraw").with(as(admin))
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/notices"));

        String board = mockMvc.perform(get("/student/notices").with(as(studentAccount)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(board).doesNotContain("Holiday notice");

        String office = mockMvc.perform(get("/notices").with(as(admin)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(office).contains("Holiday notice").contains("Withdrawn");
    }

    @Test
    void studentsAndTeachersCannotOpenTheOfficeNoticeBoard() throws Exception {
        UserAccount teacher = fixtures.teacherUser("notice.teacher");

        mockMvc.perform(get("/notices").with(as(teacher))).andExpect(status().isForbidden());
        mockMvc.perform(get("/notices").with(as(studentAccount)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/notices").with(as(studentAccount))
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .param("title", "Sneaky").param("body", "Students cannot publish.")
                        .param("audience", "ALL"))
                .andExpect(status().isForbidden());
        assertThat(announcementRepository.count()).isZero();
    }

    @Test
    void aBatchNoticeNeedsItsBatchAndATitleIsRequired() throws Exception {
        // A BATCH notice without a chosen batch is refused with a message.
        String form = publish("Room change", "Which room? The office must say.", "BATCH", null)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(form).contains("Pick the batch");
        assertThat(announcementRepository.count()).isZero();

        // A blank title fails bean validation before the service runs.
        publish("", "A body but no title.", "ALL", null)
                .andExpect(status().isOk());
        assertThat(announcementRepository.count()).isZero();
    }

    // ----- helpers ----------------------------------------------------------

    private org.springframework.test.web.servlet.ResultActions publish(
            String title, String body, String audience, Long batchId) throws Exception {
        var request = post("/notices").with(as(admin))
                .with(SecurityMockMvcRequestPostProcessors.csrf())
                .param("title", title)
                .param("body", body)
                .param("audience", audience);
        if (batchId != null) {
            request = request.param("batchId", String.valueOf(batchId));
        }
        return mockMvc.perform(request);
    }

    private UserAccount createStudentAccount(Student profile, String prefix) {
        UserForm form = new UserForm();
        form.setFullName(profile.getFullName());
        form.setEmail(prefix + "." + System.nanoTime() + "@omsai.local");
        form.setRole("STUDENT");
        form.setPassword(fixtures.safePassword());
        form.setStudentId(profile.getId());
        return userService.create(form);
    }

    private static RequestPostProcessor as(UserAccount account) {
        return user(AppUserPrincipal.from(account));
    }
}
