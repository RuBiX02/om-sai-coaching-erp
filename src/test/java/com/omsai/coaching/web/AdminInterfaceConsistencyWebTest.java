package com.omsai.coaching.web;

import com.omsai.coaching.attendance.ClassSession;
import com.omsai.coaching.attendance.ClassSessionService;
import com.omsai.coaching.attendance.SessionForm;
import com.omsai.coaching.batch.Batch;
import com.omsai.coaching.batch.BatchSubject;
import com.omsai.coaching.course.Course;
import com.omsai.coaching.enrollment.Enrollment;
import com.omsai.coaching.examination.Examination;
import com.omsai.coaching.examination.ExaminationForm;
import com.omsai.coaching.examination.ExaminationService;
import com.omsai.coaching.fees.FeeAccount;
import com.omsai.coaching.fees.FeeAccountForm;
import com.omsai.coaching.fees.FeeAccountService;
import com.omsai.coaching.security.AppUserPrincipal;
import com.omsai.coaching.security.UserAccount;
import com.omsai.coaching.student.Student;
import com.omsai.coaching.support.Fixtures;
import com.omsai.coaching.timetable.TimetableForm;
import com.omsai.coaching.timetable.TimetableService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Guards the admin-interface baseline: money is rendered through the
 * shared formatter, timetable rows start on Monday, the shared page
 * heading is present where the audit found it missing, and every
 * application page exposes the keyboard skip link and main landmark.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AdminInterfaceConsistencyWebTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private Fixtures fixtures;

    @Autowired
    private FeeAccountService feeAccountService;

    @Autowired
    private TimetableService timetableService;

    @Autowired
    private ExaminationService examinationService;

    @Autowired
    private ClassSessionService classSessionService;

    private UserAccount admin;
    private Course course;
    private Batch batch;
    private BatchSubject assignment;
    private FeeAccount account;
    private Examination examination;
    private ClassSession session;

    @BeforeEach
    void setUp() {
        admin = fixtures.adminUser("interface.consistency");
        course = fixtures.course("Interface Consistency Course");
        batch = fixtures.batch(course, "Morning");
        Student student = fixtures.student("Interface Consistency Student");
        Enrollment enrollment = fixtures.activeEnrollment(student, batch);
        assignment = fixtures.assignCourseSubject(batch,
                fixtures.assign(course, fixtures.subject("Interface Consistency Subject")));

        FeeAccountForm form = new FeeAccountForm();
        form.setEnrollmentId(enrollment.getId());
        form.setGrossFee(new BigDecimal("24000"));
        form.setDiscountAmount(new BigDecimal("4000"));
        account = feeAccountService.create(form, admin);

        ExaminationForm examinationForm = new ExaminationForm();
        examinationForm.setBatchSubjectId(assignment.getId());
        examinationForm.setTitle("Interface Consistency Exam");
        examinationForm.setExaminationDate(LocalDate.of(2026, 9, 7));
        examinationForm.setMaximumMarks(new BigDecimal("100"));
        examination = examinationService.create(examinationForm, admin);

        SessionForm sessionForm = new SessionForm();
        sessionForm.setBatchSubjectId(assignment.getId());
        sessionForm.setSessionDate(LocalDate.of(2026, 9, 7));
        sessionForm.setStartTime(LocalTime.of(9, 0));
        sessionForm.setEndTime(LocalTime.of(10, 0));
        session = classSessionService.create(sessionForm, admin);
    }

    private RequestPostProcessor asAdmin() {
        return user(AppUserPrincipal.from(admin));
    }

    private String page(String path) throws Exception {
        return mockMvc.perform(get(path).with(asAdmin()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    void feeAndPaymentAmountsUseTheSharedRupeeFormatter() throws Exception {
        String feeList = page("/fees");
        assertThat(feeList)
                .contains("₹\u00A024000.00")
                .contains("₹\u00A04000.00")
                .contains("₹\u00A020000.00");

        String paymentForm = page("/payments/new?feeAccountId=" + account.getId());
        assertThat(paymentForm).contains("₹\u00A020000.00");
    }

    @Test
    void theWeeklyTimetableStartsOnMondayAndOrdersEachDayByStartTime() throws Exception {
        timetableService.create(entry(DayOfWeek.FRIDAY, LocalTime.of(10, 0), LocalTime.of(11, 0),
                "Room F"), admin);
        timetableService.create(entry(DayOfWeek.MONDAY, LocalTime.of(11, 0), LocalTime.of(12, 0),
                "Room B"), admin);
        timetableService.create(entry(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(10, 0),
                "Room A"), admin);

        String timetable = page("/timetable");
        int monday = timetable.indexOf(">MONDAY<");
        int friday = timetable.indexOf(">FRIDAY<");
        int firstMondayClass = timetable.indexOf("09:00 – 10:00");
        int secondMondayClass = timetable.indexOf("11:00 – 12:00");

        assertThat(monday).isNotNegative();
        assertThat(friday).isGreaterThan(monday);
        assertThat(firstMondayClass).isGreaterThan(monday).isLessThan(secondMondayClass);
        assertThat(secondMondayClass).isLessThan(friday);
    }

    @Test
    void theAuditedScreensUseTheSharedPageHeading() throws Exception {
        assertThat(page("/examinations")).contains("Examination schedule");
        assertThat(page("/examinations/" + examination.getId())).contains("Examination details");
        assertThat(page("/examinations/" + examination.getId() + "/marks")).contains("Marks entry");
        assertThat(page("/timetable")).contains("Weekly schedule");
        assertThat(page("/attendance/reports")).contains("Attendance analytics");
        assertThat(page("/fees")).contains("All fee agreements");
        assertThat(page("/fees/collections")).contains("Collections overview");
        assertThat(page("/fees/" + account.getId())).contains("Interface Consistency Student");
        assertThat(page("/installments/upcoming")).contains("Due soon");
        assertThat(page("/installments/overdue")).contains("Overdue balances");
        assertThat(page("/sessions/" + session.getId())).contains("Session details");
        assertThat(page("/sessions/" + session.getId() + "/attendance")).contains("Attendance sheet");
        assertThat(page("/examinations/new")).contains("New examination");
        assertThat(page("/students/new")).contains("New admission");
        assertThat(page("/notices/new")).contains("Compose a notice");
        assertThat(page("/quizzes/new")).contains("Create a quiz");
        assertThat(page("/payments/new?feeAccountId=" + account.getId())).contains("Receive payment");
    }

    @Test
    void everyApplicationPageOffersTheSkipLinkAndMainLandmark() throws Exception {
        for (String path : new String[]{
                "/fees",
                "/timetable",
                "/attendance/reports",
                "/examinations",
                "/examinations/" + examination.getId() + "/marks",
                "/sessions/" + session.getId() + "/attendance",
                "/installments/overdue",
                "/students/new"
        }) {
            String html = page(path);
            assertThat(html)
                    .as("skip navigation on %s", path)
                    .contains("class=\"visually-hidden-focusable skip-link\" href=\"#main\"")
                    .contains("id=\"main\"");
        }
    }

    private TimetableForm entry(DayOfWeek day, LocalTime start, LocalTime end, String classroom) {
        TimetableForm form = new TimetableForm();
        form.setBatchSubjectId(assignment.getId());
        form.setDayOfWeek(day);
        form.setStartTime(start);
        form.setEndTime(end);
        form.setClassroom(classroom);
        form.setEffectiveFrom(LocalDate.of(2026, 9, 7));
        return form;
    }
}
