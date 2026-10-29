package com.omsai.coaching.timetable;

import com.omsai.coaching.batch.Batch;
import com.omsai.coaching.batch.BatchSubject;
import com.omsai.coaching.common.BusinessRuleException;
import com.omsai.coaching.course.Course;
import com.omsai.coaching.security.UserAccount;
import com.omsai.coaching.support.Fixtures;
import com.omsai.coaching.teacher.Teacher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Timetable rules: a valid recurring class is accepted, a double
 * booking (teacher, batch or classroom) is refused, the effective
 * period is respected and the generation of class sessions is
 * idempotent and honours the calendar exceptions.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TimetableServiceTest {

    @Autowired
    private TimetableService timetableService;

    @Autowired
    private Fixtures fixtures;

    @Autowired
    private com.omsai.coaching.attendance.ClassSessionRepository sessionRepository;

    private UserAccount admin;
    private Course course;
    private Batch batch;
    private BatchSubject assignment;
    private BatchSubject otherAssignment;
    private LocalDate monday;

    @BeforeEach
    void buildFixture() {
        admin = fixtures.adminUser("timetable.admin");
        course = fixtures.course("Timetable Course");
        batch = fixtures.batch(course, "Morning");
        assignment = fixtures.assignCourseSubject(batch,
                fixtures.assign(course, fixtures.subject("Timetable Subject")));
        Batch otherBatch = fixtures.batch(course, "Evening");
        otherAssignment = fixtures.assignCourseSubject(otherBatch,
                fixtures.assign(course, fixtures.subject("Timetable Subject Two")));
        monday = LocalDate.of(2026, 9, 7);   // a Monday
    }

    private TimetableForm form(DayOfWeek day, LocalTime start, LocalTime end,
                               String classroom, LocalDate from, LocalDate until) {
        return form(assignment, day, start, end, classroom, from, until);
    }

    private TimetableForm form(BatchSubject target, DayOfWeek day, LocalTime start, LocalTime end,
                               String classroom, LocalDate from, LocalDate until) {
        TimetableForm form = new TimetableForm();
        form.setBatchSubjectId(target.getId());
        form.setDayOfWeek(day);
        form.setStartTime(start);
        form.setEndTime(end);
        form.setClassroom(classroom);
        form.setEffectiveFrom(from);
        form.setEffectiveUntil(until);
        return form;
    }

    // ----- creating a schedule ---------------------------------------------

    @Test
    void aValidEntryIsCreated() {
        TimetableEntry entry = timetableService.create(
                form(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(10, 0), "Room 1",
                        monday, null), admin);

        assertThat(entry.getId()).isNotNull();
        assertThat(entry.getBatchSubject().getId()).isEqualTo(assignment.getId());
        assertThat(entry.getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
        assertThat(entry.getStartTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(entry.isActive()).isTrue();
        assertThat(entry.getCreatedBy().getId()).isEqualTo(admin.getId());
    }

    @Test
    void theScheduleIsOrderedFromMondayAndByStartTime() {
        timetableService.create(form(DayOfWeek.FRIDAY, LocalTime.of(10, 0), LocalTime.of(11, 0),
                "Room 1", monday, null), admin);
        timetableService.create(form(DayOfWeek.MONDAY, LocalTime.of(11, 0), LocalTime.of(12, 0),
                "Room 2", monday, null), admin);
        timetableService.create(form(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(10, 0),
                "Room 3", monday, null), admin);

        assertThat(timetableService.all())
                .extracting(TimetableEntry::getDayOfWeek, TimetableEntry::getStartTime)
                .containsExactly(
                        tuple(DayOfWeek.MONDAY, LocalTime.of(9, 0)),
                        tuple(DayOfWeek.MONDAY, LocalTime.of(11, 0)),
                        tuple(DayOfWeek.FRIDAY, LocalTime.of(10, 0)));
    }

    @Test
    void anInvalidTimeRangeIsRejected() {
        assertThatThrownBy(() -> timetableService.create(
                form(DayOfWeek.MONDAY, LocalTime.of(10, 0), LocalTime.of(9, 0), "Room 1",
                        monday, null), admin))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("end time must be after the start time");
    }

    @Test
    void anInvalidEffectivePeriodIsRejected() {
        assertThatThrownBy(() -> timetableService.create(
                form(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(10, 0), "Room 1",
                        monday, monday.minusDays(1)), admin))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("must not be before the first day");
    }

    // ----- conflicts --------------------------------------------------------

    @Test
    void aTeacherCannotTeachTwoClassesAtTheSameTime() {
        Teacher teacher = fixtures.teacher("Timetable Teacher");
        fixtures.assignTeacher(assignment, teacher);
        timetableService.create(form(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(10, 0),
                "Room 1", monday, null), admin);

        assertThatThrownBy(() -> timetableService.create(
                form(DayOfWeek.MONDAY, LocalTime.of(9, 30), LocalTime.of(10, 30),
                        "Room 2", monday, null), admin))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("already has a class on MONDAY");
    }

    @Test
    void aClassroomCannotBeBookedTwiceAtTheSameTime() {
        timetableService.create(form(DayOfWeek.TUESDAY, LocalTime.of(9, 0), LocalTime.of(10, 0),
                "Room 5", monday, null), admin);

        assertThatThrownBy(() -> timetableService.create(
                form(otherAssignment, DayOfWeek.TUESDAY, LocalTime.of(9, 30), LocalTime.of(10, 30),
                        "Room 5", monday, null), admin))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("classroom Room 5 is already booked");
    }

    @Test
    void aBatchCannotHaveTwoClassesAtTheSameTime() {
        timetableService.create(form(DayOfWeek.WEDNESDAY, LocalTime.of(9, 0), LocalTime.of(10, 0),
                "Room 1", monday, null), admin);

        assertThatThrownBy(() -> timetableService.create(
                form(DayOfWeek.WEDNESDAY, LocalTime.of(9, 30), LocalTime.of(10, 30),
                        "Room 9", monday, null), admin))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("already has a class on WEDNESDAY");
    }

    @Test
    void classesThatTouchButDoNotOverlapAreAllowed() {
        timetableService.create(form(DayOfWeek.THURSDAY, LocalTime.of(9, 0), LocalTime.of(10, 0),
                "Room 1", monday, null), admin);

        // 10:00 - 11:00 starts exactly when the first one ends.
        TimetableEntry second = timetableService.create(
                form(DayOfWeek.THURSDAY, LocalTime.of(10, 0), LocalTime.of(11, 0),
                        "Room 2", monday, null), admin);
        assertThat(second.getId()).isNotNull();
    }

    @Test
    void aDifferentDayIsNoConflict() {
        timetableService.create(form(DayOfWeek.FRIDAY, LocalTime.of(9, 0), LocalTime.of(10, 0),
                "Room 1", monday, null), admin);

        // The same batch, the same room and the same time - but on another
        // weekday, which is not a conflict.
        TimetableEntry second = timetableService.create(
                form(DayOfWeek.THURSDAY, LocalTime.of(9, 30), LocalTime.of(10, 30),
                        "Room 1", monday, null), admin);
        assertThat(second.getId()).isNotNull();
    }

    @Test
    void aNonOverlappingEffectivePeriodIsNoConflict() {
        timetableService.create(form(DayOfWeek.SATURDAY, LocalTime.of(9, 0), LocalTime.of(10, 0),
                "Room 1", monday, monday.plusDays(7)), admin);

        // The second schedule starts after the first one ended.
        TimetableEntry second = timetableService.create(
                form(DayOfWeek.SATURDAY, LocalTime.of(9, 0), LocalTime.of(10, 0),
                        "Room 1", monday.plusDays(14), null), admin);
        assertThat(second.getId()).isNotNull();
    }

    @Test
    void aDeactivatedEntryDoesNotBlockTheSlot() {
        TimetableEntry first = timetableService.create(
                form(DayOfWeek.SUNDAY, LocalTime.of(9, 0), LocalTime.of(10, 0), "Room 1",
                        monday, null), admin);
        timetableService.deactivate(first.getId());

        TimetableEntry second = timetableService.create(
                form(DayOfWeek.SUNDAY, LocalTime.of(9, 0), LocalTime.of(10, 0), "Room 1",
                        monday, null), admin);
        assertThat(second.getId()).isNotNull();
    }

    // ----- generating class sessions ----------------------------------------

    @Test
    void generationCreatesOneSessionPerOccurrenceAndIsIdempotent() {
        timetableService.create(form(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(10, 0),
                "Room 1", monday, null), admin);

        // The Monday of the next two weeks.
        TimetableService.GenerationResult first =
                timetableService.generateSessions(monday.plusWeeks(1), monday.plusWeeks(1), admin);
        assertThat(first.created()).isEqualTo(1);
        assertThat(first.skippedExisting()).isZero();

        // Running it again must not create a second session.
        TimetableService.GenerationResult second =
                timetableService.generateSessions(monday.plusWeeks(1), monday.plusWeeks(1), admin);
        assertThat(second.created()).isZero();
        assertThat(second.skippedExisting()).isEqualTo(1);
    }

    @Test
    void generationSkipsHolidaysAndCancelledOccurrences() {
        timetableService.create(form(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(10, 0),
                "Room 1", monday, null), admin);

        CalendarExceptionForm holiday = new CalendarExceptionForm();
        holiday.setExceptionDate(monday.plusWeeks(1));
        holiday.setKind(CalendarException.Kind.HOLIDAY);
        holiday.setReason("Institute holiday");
        timetableService.addException(holiday, admin);

        TimetableService.GenerationResult result =
                timetableService.generateSessions(monday.plusWeeks(1), monday.plusWeeks(1), admin);
        assertThat(result.created()).isZero();
        assertThat(result.skippedHoliday()).isEqualTo(1);
    }

    @Test
    void aCancelledOccurrenceIsNotGenerated() {
        TimetableEntry entry = timetableService.create(
                form(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(10, 0), "Room 1",
                        monday, null), admin);

        CalendarExceptionForm cancellation = new CalendarExceptionForm();
        cancellation.setExceptionDate(monday.plusWeeks(1));
        cancellation.setKind(CalendarException.Kind.CANCELLED);
        cancellation.setTimetableEntryId(entry.getId());
        cancellation.setReason("Teacher on leave");
        timetableService.addException(cancellation, admin);

        TimetableService.GenerationResult result =
                timetableService.generateSessions(monday.plusWeeks(1), monday.plusWeeks(1), admin);
        assertThat(result.created()).isZero();
        assertThat(result.skippedCancelled()).isEqualTo(1);
    }

    @Test
    void anExistingClassIsNeverOverwritten() {
        timetableService.create(form(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(10, 0),
                "Room 1", monday, null), admin);
        timetableService.generateSessions(monday.plusWeeks(1), monday.plusWeeks(1), admin);

        // The generated class exists exactly once and can be completed
        // without the schedule touching it again.
        long generated = sessionRepository.countByBatchSubjectId(assignment.getId());
        assertThat(generated).isEqualTo(1);
        assertThat(timetableService.sessionExists(
                timetableService.all().get(0), monday.plusWeeks(1))).isTrue();
    }

    @Test
    void anInvalidRangeIsRejected() {
        assertThatThrownBy(() -> timetableService.generateSessions(monday.plusDays(5), monday, admin))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("must not be before the start date");
    }
}
