package com.omsai.coaching.timetable;

import com.omsai.coaching.attendance.ClassSessionRepository;
import com.omsai.coaching.attendance.ClassSessionService;
import com.omsai.coaching.attendance.SessionForm;
import com.omsai.coaching.batch.BatchSubject;
import com.omsai.coaching.batch.BatchSubjectRepository;
import com.omsai.coaching.common.BusinessClock;
import com.omsai.coaching.common.BusinessRuleException;
import com.omsai.coaching.security.UserAccount;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Timetable rules.
 *
 * <p>A schedule is a planned recurring class; a class session is one
 * occurrence of it. The two are linked by the batch-subject assignment
 * and the date - generating sessions never touches an existing one.</p>
 *
 * <p>Conflicts are rejected on the server: the same teacher, the same
 * classroom or the same batch may not be booked twice at the same
 * time. Two time ranges overlap when {@code startA < endB AND startB <
 * endA}, so a class ending exactly when the next one begins is
 * fine.</p>
 */
@Service
public class TimetableService {

    private final TimetableEntryRepository entryRepository;
    private final CalendarExceptionRepository exceptionRepository;
    private final BatchSubjectRepository batchSubjectRepository;
    private final ClassSessionRepository sessionRepository;
    private final ClassSessionService sessionService;
    private final BusinessClock businessClock;

    public TimetableService(TimetableEntryRepository entryRepository,
                            CalendarExceptionRepository exceptionRepository,
                            BatchSubjectRepository batchSubjectRepository,
                            ClassSessionRepository sessionRepository,
                            ClassSessionService sessionService,
                            BusinessClock businessClock) {
        this.entryRepository = entryRepository;
        this.exceptionRepository = exceptionRepository;
        this.batchSubjectRepository = batchSubjectRepository;
        this.sessionRepository = sessionRepository;
        this.sessionService = sessionService;
        this.businessClock = businessClock;
    }

    // ----- schedules -------------------------------------------------------

    /**
     * Creates a recurring schedule. The assignment must exist; the
     * teacher, the batch and the classroom must not be double booked.
     */
    @Transactional
    public TimetableEntry create(TimetableForm form, UserAccount createdBy) {
        BatchSubject assignment = batchSubjectRepository.findById(form.getBatchSubjectId())
                .orElseThrow(() -> new BusinessRuleException("The batch subject assignment does not exist."));
        validateTimes(form.getStartTime(), form.getEndTime());
        validatePeriod(form.getEffectiveFrom(), form.getEffectiveUntil());

        TimetableEntry entry = new TimetableEntry();
        entry.setBatchSubject(assignment);
        applyForm(entry, form);
        entry.setCreatedBy(createdBy);
        checkConflicts(entry);
        return entryRepository.save(entry);
    }

    /** Updates a schedule. The history of the classes it generated is never touched. */
    @Transactional
    public TimetableEntry update(Long id, TimetableForm form) {
        TimetableEntry entry = entryRepository.findById(id)
                .orElseThrow(() -> new BusinessRuleException("Timetable entry not found."));
        validateTimes(form.getStartTime(), form.getEndTime());
        validatePeriod(form.getEffectiveFrom(), form.getEffectiveUntil());
        applyForm(entry, form);
        checkConflicts(entry);
        return entryRepository.save(entry);
    }

    /** Deactivates a schedule; the generated classes and their attendance stay. */
    @Transactional
    public TimetableEntry deactivate(Long id) {
        TimetableEntry entry = entryRepository.findById(id)
                .orElseThrow(() -> new BusinessRuleException("Timetable entry not found."));
        entry.setActive(false);
        return entryRepository.save(entry);
    }

    @Transactional(readOnly = true)
    public TimetableEntry get(Long id) {
        return entryRepository.findWithContextById(id)
                .orElseThrow(() -> new BusinessRuleException("Timetable entry not found."));
    }

    @Transactional(readOnly = true)
    public List<TimetableEntry> all() {
        return entryRepository.findAllWithContext();
    }

    @Transactional(readOnly = true)
    public List<TimetableEntry> forTeacher(Long teacherId) {
        return entryRepository.findForTeacher(teacherId);
    }

    @Transactional(readOnly = true)
    public List<TimetableEntry> forBatch(Long batchId) {
        return entryRepository.findForBatch(batchId);
    }

    @Transactional(readOnly = true)
    public List<TimetableEntry> forClassroom(String classroom) {
        return entryRepository.findForClassroom(classroom);
    }

    @Transactional(readOnly = true)
    public List<TimetableEntry> forDay(DayOfWeek dayOfWeek) {
        return entryRepository.findActiveByDay(dayOfWeek);
    }

    /**
     * The schedules that apply on a date: the weekday's active entries
     * whose effective period covers the date.
     */
    @Transactional(readOnly = true)
    public List<TimetableEntry> occurringOn(LocalDate date) {
        return entryRepository.findActiveByDay(date.getDayOfWeek()).stream()
                .filter(entry -> entry.appliesOn(date))
                .toList();
    }

    // ----- conflicts -------------------------------------------------------

    /**
     * Rejects a schedule that would double book the teacher, the
     * classroom or the batch.
     */
    /** The far end of the effective period, used when a schedule has no end date. */
    private static final LocalDate NO_END = LocalDate.of(9999, 12, 31);

    private void checkConflicts(TimetableEntry entry) {
        BatchSubject assignment = entry.getBatchSubject();
        for (TimetableEntry other : entryRepository.findPotentialConflicts(
                entry.getId() == null ? -1L : entry.getId(),
                entry.getDayOfWeek(), entry.getStartTime(), entry.getEndTime(),
                entry.getEffectiveFrom(),
                entry.getEffectiveUntil() == null ? NO_END : entry.getEffectiveUntil())) {
            BatchSubject otherAssignment = other.getBatchSubject();
            if (otherAssignment.getTeacher() != null
                    && assignment.getTeacher() != null
                    && otherAssignment.getTeacher().getId().equals(assignment.getTeacher().getId())) {
                throw new BusinessRuleException("The teacher " + assignment.getTeacher().getFullName()
                        + " already has a class on " + entry.getDayOfWeek() + " from "
                        + other.getStartTime() + " to " + other.getEndTime() + ".");
            }
            if (otherAssignment.getBatch().getId().equals(assignment.getBatch().getId())) {
                throw new BusinessRuleException("The batch " + assignment.getBatch().getName()
                        + " already has a class on " + entry.getDayOfWeek() + " from "
                        + other.getStartTime() + " to " + other.getEndTime() + ".");
            }
            if (entry.getClassroom() != null && !entry.getClassroom().isBlank()
                    && entry.getClassroom().equalsIgnoreCase(other.getClassroom())) {
                throw new BusinessRuleException("The classroom " + entry.getClassroom()
                        + " is already booked on " + entry.getDayOfWeek() + " from "
                        + other.getStartTime() + " to " + other.getEndTime() + ".");
            }
        }
    }

    // ----- calendar exceptions ---------------------------------------------

    /** Records a holiday, a cancelled occurrence or a rescheduling. */
    @Transactional
    public CalendarException addException(CalendarExceptionForm form, UserAccount createdBy) {
        CalendarException exception = new CalendarException();
        exception.setExceptionDate(form.getExceptionDate());
        exception.setKind(form.getKind());
        exception.setReason(form.getReason());
        exception.setRescheduledTo(form.getRescheduledTo());
        exception.setCreatedBy(createdBy);
        if (form.getKind() != CalendarException.Kind.HOLIDAY) {
            TimetableEntry entry = entryRepository.findById(form.getTimetableEntryId())
                    .orElseThrow(() -> new BusinessRuleException("Timetable entry not found."));
            exception.setTimetableEntry(entry);
        }
        return exceptionRepository.save(exception);
    }

    @Transactional
    public void removeException(Long id) {
        CalendarException exception = exceptionRepository.findById(id)
                .orElseThrow(() -> new BusinessRuleException("Calendar exception not found."));
        exceptionRepository.delete(exception);
    }

    @Transactional(readOnly = true)
    public List<CalendarException> exceptionsBetween(LocalDate from, LocalDate to) {
        return exceptionRepository.findBetween(from, to);
    }

    @Transactional(readOnly = true)
    public List<CalendarException> exceptionsOn(LocalDate date) {
        return exceptionRepository.findForDate(date);
    }

    /** True when the institute is closed on that date. */
    @Transactional(readOnly = true)
    public boolean isHoliday(LocalDate date) {
        return exceptionRepository.countHolidays(date) > 0;
    }

    /** True when this occurrence was cancelled. */
    @Transactional(readOnly = true)
    public boolean isCancelled(LocalDate date, TimetableEntry entry) {
        return exceptionRepository.countCancellations(date, entry.getId()) > 0;
    }

    // ----- generating class sessions ---------------------------------------

    /** The result of a generation run. */
    public record GenerationResult(int created, int skippedExisting, int skippedHoliday,
                                   int skippedCancelled, LocalDate from, LocalDate to) {
    }

    /**
     * Generates the class sessions of a date range from the recurring
     * schedule.
     *
     * <p>The run is idempotent: a session that already exists for the
     * same assignment and date is never touched or duplicated - a
     * manually created class simply stays as it is. Holidays and
     * cancelled occurrences are skipped.</p>
     */
    @Transactional
    public GenerationResult generateSessions(LocalDate from, LocalDate to, UserAccount createdBy) {
        if (from == null || to == null) {
            throw new BusinessRuleException("Please choose the date range to generate.");
        }
        if (to.isBefore(from)) {
            throw new BusinessRuleException("The end date must not be before the start date.");
        }
        int created = 0;
        int skippedExisting = 0;
        int skippedHoliday = 0;
        int skippedCancelled = 0;
        LocalDate date = from;
        while (!date.isAfter(to)) {
            if (isHoliday(date)) {
                skippedHoliday++;
                date = date.plusDays(1);
                continue;
            }
            for (TimetableEntry entry : occurringOn(date)) {
                if (isCancelled(date, entry)) {
                    skippedCancelled++;
                    continue;
                }
                if (sessionExists(entry, date)) {
                    skippedExisting++;
                    continue;
                }
                createSession(entry, date, createdBy);
                created++;
            }
            date = date.plusDays(1);
        }
        return new GenerationResult(created, skippedExisting, skippedHoliday, skippedCancelled, from, to);
    }

    /** True when a class session already exists for that assignment and date. */
    @Transactional(readOnly = true)
    public boolean sessionExists(TimetableEntry entry, LocalDate date) {
        return sessionRepository
                .findByBatchSubjectIdAndSessionDate(entry.getBatchSubject().getId(), date)
                .isPresent();
    }

    /**
     * Creates the class session of one occurrence through the existing
     * session service, so the established rules (active batch and
     * course, valid times) apply.
     */
    private void createSession(TimetableEntry entry, LocalDate date, UserAccount createdBy) {
        SessionForm form = new SessionForm();
        form.setBatchSubjectId(entry.getBatchSubject().getId());
        form.setSessionDate(date);
        form.setStartTime(entry.getStartTime());
        form.setEndTime(entry.getEndTime());
        form.setTopic(entry.getTopic() == null ? "" : entry.getTopic());
        sessionService.create(form, createdBy);
    }

    // ----- helpers ---------------------------------------------------------

    private static void applyForm(TimetableEntry entry, TimetableForm form) {
        entry.setDayOfWeek(form.getDayOfWeek());
        entry.setStartTime(form.getStartTime());
        entry.setEndTime(form.getEndTime());
        entry.setClassroom(form.getClassroom() == null || form.getClassroom().isBlank()
                ? null : form.getClassroom().trim());
        entry.setTopic(form.getTopic() == null || form.getTopic().isBlank() ? null : form.getTopic().trim());
        entry.setEffectiveFrom(form.getEffectiveFrom());
        entry.setEffectiveUntil(form.getEffectiveUntil());
        entry.setActive(form.isActive());
    }

    private static void validateTimes(LocalTime start, LocalTime end) {
        if (start == null || end == null) {
            throw new BusinessRuleException("The start and end time are required.");
        }
        if (!end.isAfter(start)) {
            throw new BusinessRuleException("The end time must be after the start time.");
        }
    }

    private static void validatePeriod(LocalDate from, LocalDate until) {
        if (from == null) {
            throw new BusinessRuleException("The first day of the schedule is required.");
        }
        if (until != null && until.isBefore(from)) {
            throw new BusinessRuleException("The last day of the schedule must not be before the first day.");
        }
    }

    /** The schedule entries of a week, ordered by weekday and time. */
    @Transactional(readOnly = true)
    public List<TimetableEntry> weekly(LocalDate weekStart) {
        List<TimetableEntry> entries = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            entries.addAll(occurringOn(weekStart.plusDays(i)));
        }
        return entries;
    }
}
