package com.omsai.coaching.web;

import com.omsai.coaching.batch.Batch;
import com.omsai.coaching.batch.BatchSubject;
import com.omsai.coaching.course.Course;
import com.omsai.coaching.enrollment.Enrollment;
import com.omsai.coaching.attendance.Attendance;
import com.omsai.coaching.attendance.AttendanceForm;
import com.omsai.coaching.attendance.AttendanceService;
import com.omsai.coaching.attendance.ClassSession;
import com.omsai.coaching.attendance.ClassSessionService;
import com.omsai.coaching.attendance.SessionForm;
import com.omsai.coaching.security.AppUserPrincipal;
import com.omsai.coaching.security.UserAccount;
import com.omsai.coaching.support.Fixtures;
import com.omsai.coaching.teacher.Teacher;
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

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The printable attendance sheet of a class session: who may open it,
 * whose roster it shows and what it contains. The sheet is meant for
 * paper, so it must not carry any fee or payment information.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AttendanceSheetWebTest {

    private static final LocalDate CLASS_DATE = LocalDate.of(2026, 9, 10);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private Fixtures fixtures;

    @Autowired
    private ClassSessionService sessionService;

    @Autowired
    private AttendanceService attendanceService;

    private Course course;
    private Batch batch;
    private BatchSubject assignment;
    private UserAccount admin;
    private UserAccount ownerTeacher;
    private UserAccount otherTeacher;
    private ClassSession session;
    private Enrollment onRoster;
    private Enrollment joinedLater;
    private Enrollment leftEarlier;

    @BeforeEach
    void buildFixture() {
        course = fixtures.course("Sheet Course");
        batch = fixtures.batch(course, "Evening");
        assignment = fixtures.assignCourseSubject(batch,
                fixtures.assign(course, fixtures.subject("Sheet Subject")));

        admin = fixtures.adminUser("sheet.admin");

        UserAccount owner = fixtures.teacherUser("sheet.owner");
        Teacher ownerProfile = fixtures.teacherLinkedTo(owner, "Sheet Owner");
        fixtures.assignTeacher(assignment, ownerProfile);
        ownerTeacher = owner;

        otherTeacher = fixtures.teacherUser("sheet.outsider");
        fixtures.teacherLinkedTo(otherTeacher, "Sheet Outsider");

        onRoster = fixtures.activeEnrollment(fixtures.student("Roster Student"), batch, CLASS_DATE.minusDays(9));
        // Enrolled after the class: not on the sheet.
        joinedLater = fixtures.activeEnrollment(fixtures.student("Late Student"), batch, CLASS_DATE.plusDays(5));
        // Enrolled before, but the period ended before the class.
        leftEarlier = fixtures.activeEnrollment(fixtures.student("Former Student"), batch, CLASS_DATE.minusDays(20));
        leftEarlier.setEndDate(CLASS_DATE.minusDays(2));
        leftEarlier.setStatus(Enrollment.Status.WITHDRAWN);

        SessionForm form = new SessionForm();
        form.setBatchSubjectId(assignment.getId());
        form.setSessionDate(CLASS_DATE);
        form.setStartTime(LocalTime.of(17, 0));
        form.setEndTime(LocalTime.of(18, 30));
        form.setTopic("Geometry revision");
        session = sessionService.create(form, admin);

        AttendanceForm attendanceForm = new AttendanceForm();
        AttendanceForm.Row row = new AttendanceForm.Row();
        row.setEnrollmentId(onRoster.getId());
        row.setStatus(Attendance.Status.LATE);
        row.setRemarks("came after the break");
        attendanceForm.setRows(List.of(row));
        attendanceService.record(session.getId(), attendanceForm, admin);
    }

    @Test
    void administratorCanOpenThePrintableSheet() throws Exception {
        String html = sheet();

        assertThat(html).contains("Om Sai Coaching");
        assertThat(html).contains("Attendance sheet");
        assertThat(html).contains(course.getName());
        assertThat(html).contains(batch.getName());
        assertThat(html).contains("Sheet Subject");
        assertThat(html).contains("10 September 2026");
        assertThat(html).contains("Sheet Owner");
        assertThat(html).contains("Geometry revision");
        assertThat(html).contains(onRoster.getStudent().getStudentCode());
        assertThat(html).contains("Roster Student");
    }

    @Test
    void theAssignedTeacherCanOpenTheirOwnSheet() throws Exception {
        mockMvc.perform(get("/sessions/{id}/print", session.getId()).with(as(ownerTeacher)))
                .andExpect(status().isOk());
    }

    @Test
    void anotherTeacherIsDeniedTheSheet() throws Exception {
        mockMvc.perform(get("/sessions/{id}/print", session.getId()).with(as(otherTeacher)))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymousVisitorsAreSentToTheLoginPage() throws Exception {
        mockMvc.perform(get("/sessions/{id}/print", session.getId()))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void onlyStudentsOnTheRosterOfThatDateAreListed() throws Exception {
        String html = sheet();

        assertThat(html).contains("Roster Student");
        assertThat(html).doesNotContain("Late Student");
        assertThat(html).doesNotContain("Former Student");
    }

    @Test
    void recordedAttendanceIsVisibleOnTheSheet() throws Exception {
        String html = sheet();

        // The recorded mark is shown as a tick in the matching column.
        assertThat(html).contains("title=\"LATE\"");
        assertThat(html).contains("came after the break");
    }

    @Test
    void thePrintStylesheetIsLinked() throws Exception {
        assertThat(sheet()).contains("/css/attendance-sheet.css");
    }

    @Test
    void theSheetCarriesNoFeeOrPaymentInformation() throws Exception {
        String html = sheet();

        assertThat(html).doesNotContain("Outstanding balance");
        assertThat(html).doesNotContain("Payment");
        assertThat(html).doesNotContain("Receipt");
        assertThat(html).doesNotContain("Gross fee");
    }

    // ----- helpers ----------------------------------------------------------

    private String sheet() throws Exception {
        return mockMvc.perform(get("/sessions/{id}/print", session.getId()).with(as(admin)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private static RequestPostProcessor as(UserAccount account) {
        return user(AppUserPrincipal.from(account));
    }
}
