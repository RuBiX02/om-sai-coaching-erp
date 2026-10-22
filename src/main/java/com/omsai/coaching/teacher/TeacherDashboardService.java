package com.omsai.coaching.teacher;

import com.omsai.coaching.attendance.AttendanceRepository;
import com.omsai.coaching.attendance.ClassSession;
import com.omsai.coaching.attendance.ClassSessionRepository;
import com.omsai.coaching.examination.Examination;
import com.omsai.coaching.examination.ExaminationService;
import com.omsai.coaching.timetable.TimetableEntry;
import com.omsai.coaching.timetable.TimetableService;
import com.omsai.coaching.common.BusinessClock;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * The teacher's own dashboard: only what belongs to the classes the
 * teacher is assigned to. Nothing here reaches beyond the teacher's
 * own assignments, and a teacher without a profile or without classes
 * simply gets an empty page instead of an error.
 */
@Service
public class TeacherDashboardService {

    private final TeacherService teacherService;
    private final ClassSessionRepository sessionRepository;
    private final AttendanceRepository attendanceRepository;
    private final TimetableService timetableService;
    private final ExaminationService examinationService;
    private final BusinessClock businessClock;

    public TeacherDashboardService(TeacherService teacherService,
                                   ClassSessionRepository sessionRepository,
                                   AttendanceRepository attendanceRepository,
                                   TimetableService timetableService,
                                   ExaminationService examinationService,
                                   BusinessClock businessClock) {
        this.teacherService = teacherService;
        this.sessionRepository = sessionRepository;
        this.attendanceRepository = attendanceRepository;
        this.timetableService = timetableService;
        this.examinationService = examinationService;
        this.businessClock = businessClock;
    }

    /** The teacher profile behind the signed-in account (null when unlinked). */
    @Transactional(readOnly = true)
    public Teacher profileOf(Long userId) {
        return teacherService.findByUser(userId);
    }

    /** The classes of the teacher on a date. */
    @Transactional(readOnly = true)
    public List<TimetableEntry> classesOn(Long teacherId, LocalDate date) {
        return timetableService.forTeacher(teacherId).stream()
                .filter(entry -> entry.isActive()
                        && entry.getDayOfWeek() == date.getDayOfWeek()
                        && entry.appliesOn(date))
                .sorted(java.util.Comparator.comparing(TimetableEntry::getStartTime))
                .toList();
    }

    /** The teacher's whole weekly schedule. */
    @Transactional(readOnly = true)
    public List<TimetableEntry> weeklySchedule(Long teacherId) {
        return timetableService.forTeacher(teacherId);
    }

    /** The far end of the search range, so no query needs a null date. */
    private static final LocalDate FAR_FUTURE = LocalDate.of(2099, 12, 31);

    /** The teacher's classes from today on. */
    @Transactional(readOnly = true)
    public List<ClassSession> upcomingSessions(Long teacherId) {
        return sessionRepository
                .searchForTeacherBetween(teacherId, businessClock.today(), FAR_FUTURE,
                        PageRequest.of(0, 5))
                .getContent();
    }

    /** The teacher's classes of the past weeks. */
    @Transactional(readOnly = true)
    public List<ClassSession> recentSessions(Long teacherId) {
        return sessionRepository
                .searchForTeacherBetween(teacherId, LocalDate.of(2000, 1, 1),
                        businessClock.today().minusDays(30), PageRequest.of(0, 5))
                .getContent();
    }

    /** The open classes of the teacher whose attendance is still missing. */
    @Transactional(readOnly = true)
    public List<ClassSession> attendancePending(Long teacherId) {
        return sessionRepository
                .searchForTeacherBetweenWithStatus(teacherId, businessClock.today(), FAR_FUTURE,
                        ClassSession.Status.OPEN, PageRequest.of(0, 10))
                .getContent().stream()
                .filter(session -> attendanceRepository.countByClassSessionId(session.getId()) == 0)
                .toList();
    }

    /** The examinations of the teacher's classes that still need marks. */
    @Transactional(readOnly = true)
    public List<Examination> examinations(Long teacherId) {
        return examinationService.forTeacher(teacherId).stream()
                .filter(examination -> examination.getStatus() != Examination.Status.CANCELLED)
                .toList();
    }
}
