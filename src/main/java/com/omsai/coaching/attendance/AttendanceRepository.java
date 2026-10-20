package com.omsai.coaching.attendance;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AttendanceRepository extends JpaRepository<Attendance, Long> {

    Optional<Attendance> findByClassSessionIdAndEnrollmentId(Long classSessionId, Long enrollmentId);

    /**
     * Attendance of one session with enrollment, student and marker
     * context (for the session detail and attendance marking pages).
     */
    @Query("""
            select a from Attendance a
            join fetch a.enrollment e
            join fetch e.student s
            join fetch a.markedBy m
            where a.classSession.id = :sessionId
            order by s.fullName, s.id
            """)
    List<Attendance> findForSession(@Param("sessionId") Long sessionId);

    boolean existsByClassSessionId(Long classSessionId);

    long countByClassSessionId(Long classSessionId);

    // ----- aggregate report queries (all values come from the database) --

    /**
     * Attendance counts per enrollment for sessions of one batch.
     * Only completed sessions count (cancelled and open sessions never
     * enter a denominator) and rows are restricted to enrollments whose
     * effective period covers the session date.
     */
    @Query(value = """
            select e.id as enrollmentId,
                   sum(case when a.status = 'PRESENT' then 1 else 0 end) as presentCount,
                   sum(case when a.status = 'ABSENT'  then 1 else 0 end) as absentCount,
                   sum(case when a.status = 'LATE'    then 1 else 0 end) as lateCount,
                   sum(case when a.status = 'EXCUSED' then 1 else 0 end) as excusedCount
            from attendance a
            join class_sessions cs on cs.id = a.class_session_id
            join batch_subjects bs on bs.id = cs.batch_subject_id
            join enrollments e on e.id = a.enrollment_id
            where bs.batch_id = :batchId
              and cs.status = 'COMPLETED'
              and (cast(:from as date) is null or cs.session_date >= cast(:from as date))
              and (cast(:to as date) is null or cs.session_date <= cast(:to as date))
              and cs.session_date >= e.enrollment_date
              and (e.end_date is null or e.end_date >= cs.session_date)
              and e.batch_id = bs.batch_id
            group by e.id
            """, nativeQuery = true)
    List<EnrollmentAttendanceRow> attendanceCountsForBatch(@Param("batchId") Long batchId,
                                                           @Param("from") LocalDate from,
                                                           @Param("to") LocalDate to);

    /**
     * Roster-based denominator per enrollment: the number of
     * completed sessions of the batch that fall inside the
     * enrollment's effective period, regardless of whether an
     * attendance row exists. This is the "eligible completed
     * sessions" count for the batch report.
     */
    @Query(value = """
            select e.id as enrollmentId,
                   s.student_code as studentCode,
                   s.full_name as studentName,
                   e.enrollment_date as enrolledFrom,
                   e.end_date as enrolledTo,
                   count(cs.id) as sessionCount
            from enrollments e
            join students s on s.id = e.student_id
            join class_sessions cs on cs.status = 'COMPLETED'
                 and cs.session_date >= e.enrollment_date
                 and (e.end_date is null or e.end_date >= cs.session_date)
                 and (cast(:from as date) is null or cs.session_date >= cast(:from as date))
                 and (cast(:to as date) is null or cs.session_date <= cast(:to as date))
                 and exists (select 1 from batch_subjects bs
                             where bs.id = cs.batch_subject_id and bs.batch_id = e.batch_id)
            where e.batch_id = :batchId
            group by e.id, s.student_code, s.full_name, e.enrollment_date, e.end_date
            order by s.full_name, e.id
            """, nativeQuery = true)
    List<EnrollmentSessionRow> sessionCountsForBatch(@Param("batchId") Long batchId,
                                                     @Param("from") LocalDate from,
                                                     @Param("to") LocalDate to);

    /** Attendance counts per enrollment for sessions of one batch-subject assignment. */
    @Query(value = """
            select e.id as enrollmentId,
                   sum(case when a.status = 'PRESENT' then 1 else 0 end) as presentCount,
                   sum(case when a.status = 'ABSENT'  then 1 else 0 end) as absentCount,
                   sum(case when a.status = 'LATE'    then 1 else 0 end) as lateCount,
                   sum(case when a.status = 'EXCUSED' then 1 else 0 end) as excusedCount
            from attendance a
            join class_sessions cs on cs.id = a.class_session_id
            join enrollments e on e.id = a.enrollment_id
            where cs.batch_subject_id = :batchSubjectId
              and cs.status = 'COMPLETED'
              and (cast(:from as date) is null or cs.session_date >= cast(:from as date))
              and (cast(:to as date) is null or cs.session_date <= cast(:to as date))
              and cs.session_date >= e.enrollment_date
              and (e.end_date is null or e.end_date >= cs.session_date)
            group by e.id
            """, nativeQuery = true)
    List<EnrollmentAttendanceRow> attendanceCountsForBatchSubject(@Param("batchSubjectId") Long batchSubjectId,
                                                                  @Param("from") LocalDate from,
                                                                  @Param("to") LocalDate to);

    /** Denominator per enrollment for the subject report. */
    @Query(value = """
            select e.id as enrollmentId,
                   s.student_code as studentCode,
                   s.full_name as studentName,
                   e.enrollment_date as enrolledFrom,
                   e.end_date as enrolledTo,
                   count(cs.id) as sessionCount
            from enrollments e
            join students s on s.id = e.student_id
            join class_sessions cs on cs.batch_subject_id = :batchSubjectId
                 and cs.status = 'COMPLETED'
                 and cs.session_date >= e.enrollment_date
                 and (e.end_date is null or e.end_date >= cs.session_date)
                 and (cast(:from as date) is null or cs.session_date >= cast(:from as date))
                 and (cast(:to as date) is null or cs.session_date <= cast(:to as date))
            where e.batch_id = (select bs.batch_id from batch_subjects bs where bs.id = :batchSubjectId)
            group by e.id, s.student_code, s.full_name, e.enrollment_date, e.end_date
            order by s.full_name, e.id
            """, nativeQuery = true)
    List<EnrollmentSessionRow> sessionCountsForBatchSubject(@Param("batchSubjectId") Long batchSubjectId,
                                                            @Param("from") LocalDate from,
                                                            @Param("to") LocalDate to);

    /** Attendance counts per enrollment for one student (all her enrollment rows). */
    @Query(value = """
            select e.id as enrollmentId,
                   sum(case when a.status = 'PRESENT' then 1 else 0 end) as presentCount,
                   sum(case when a.status = 'ABSENT'  then 1 else 0 end) as absentCount,
                   sum(case when a.status = 'LATE'    then 1 else 0 end) as lateCount,
                   sum(case when a.status = 'EXCUSED' then 1 else 0 end) as excusedCount
            from attendance a
            join class_sessions cs on cs.id = a.class_session_id
            join enrollments e on e.id = a.enrollment_id
            where e.student_id = :studentId
              and cs.status = 'COMPLETED'
              and (cast(:from as date) is null or cs.session_date >= cast(:from as date))
              and (cast(:to as date) is null or cs.session_date <= cast(:to as date))
              and cs.session_date >= e.enrollment_date
              and (e.end_date is null or e.end_date >= cs.session_date)
            group by e.id
            """, nativeQuery = true)
    List<EnrollmentAttendanceRow> attendanceCountsForStudent(@Param("studentId") Long studentId,
                                                             @Param("from") LocalDate from,
                                                             @Param("to") LocalDate to);

    /** Denominator per enrollment for one student's report. */
    @Query(value = """
            select e.id as enrollmentId,
                   s.student_code as studentCode,
                   s.full_name as studentName,
                   e.enrollment_date as enrolledFrom,
                   e.end_date as enrolledTo,
                   count(cs.id) as sessionCount
            from enrollments e
            join students s on s.id = e.student_id
            join class_sessions cs on cs.status = 'COMPLETED'
                 and cs.session_date >= e.enrollment_date
                 and (e.end_date is null or e.end_date >= cs.session_date)
                 and (cast(:from as date) is null or cs.session_date >= cast(:from as date))
                 and (cast(:to as date) is null or cs.session_date <= cast(:to as date))
                 and exists (select 1 from batch_subjects bs
                             where bs.id = cs.batch_subject_id and bs.batch_id = e.batch_id)
            where e.student_id = :studentId
            group by e.id, s.student_code, s.full_name, e.enrollment_date, e.end_date
            order by e.enrollment_date, e.id
            """, nativeQuery = true)
    List<EnrollmentSessionRow> sessionCountsForStudent(@Param("studentId") Long studentId,
                                                       @Param("from") LocalDate from,
                                                       @Param("to") LocalDate to);

    /** Per-session attendance counts for one date (date-wise report and dashboard). */
    @Query(value = """
            select a.class_session_id as sessionId,
                   sum(case when a.status = 'PRESENT' then 1 else 0 end) as presentCount,
                   sum(case when a.status = 'ABSENT'  then 1 else 0 end) as absentCount,
                   sum(case when a.status = 'LATE'    then 1 else 0 end) as lateCount,
                   sum(case when a.status = 'EXCUSED' then 1 else 0 end) as excusedCount
            from attendance a
            join class_sessions cs on cs.id = a.class_session_id
            where cs.session_date = :date
            group by a.class_session_id
            """, nativeQuery = true)
    List<SessionCountRow> countRowsForDate(@Param("date") LocalDate date);
}
