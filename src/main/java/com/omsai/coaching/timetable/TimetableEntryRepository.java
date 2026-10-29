package com.omsai.coaching.timetable;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

public interface TimetableEntryRepository extends JpaRepository<TimetableEntry, Long> {

    /** The schedule of one batch-subject assignment. */
    @Query("""
            select e from TimetableEntry e
            join fetch e.batchSubject bs
            join fetch bs.batch b
            join fetch b.course c
            join fetch bs.courseSubject cs
            join fetch cs.subject s
            left join fetch e.createdBy u
            where bs.id = :batchSubjectId
            order by case e.dayOfWeek
                when java.time.DayOfWeek.MONDAY then 1
                when java.time.DayOfWeek.TUESDAY then 2
                when java.time.DayOfWeek.WEDNESDAY then 3
                when java.time.DayOfWeek.THURSDAY then 4
                when java.time.DayOfWeek.FRIDAY then 5
                when java.time.DayOfWeek.SATURDAY then 6
                when java.time.DayOfWeek.SUNDAY then 7
                else 8 end, e.startTime
            """)
    List<TimetableEntry> findForBatchSubject(@Param("batchSubjectId") Long batchSubjectId);

    /** How many schedules point at one assignment (a deletion guard). */
    long countByBatchSubjectId(Long batchSubjectId);

    /** Every schedule with its context (used by the weekly view and the filters). */
    @Query("""
            select e from TimetableEntry e
            join fetch e.batchSubject bs
            join fetch bs.batch b
            join fetch b.course c
            join fetch bs.courseSubject cs
            join fetch cs.subject s
            left join fetch bs.teacher t
            left join fetch e.createdBy u
            order by case e.dayOfWeek
                when java.time.DayOfWeek.MONDAY then 1
                when java.time.DayOfWeek.TUESDAY then 2
                when java.time.DayOfWeek.WEDNESDAY then 3
                when java.time.DayOfWeek.THURSDAY then 4
                when java.time.DayOfWeek.FRIDAY then 5
                when java.time.DayOfWeek.SATURDAY then 6
                when java.time.DayOfWeek.SUNDAY then 7
                else 8 end, e.startTime, s.name
            """)
    List<TimetableEntry> findAllWithContext();

    /** The active schedules of one weekday. */
    @Query("""
            select e from TimetableEntry e
            join fetch e.batchSubject bs
            join fetch bs.batch b
            join fetch b.course c
            join fetch bs.courseSubject cs
            join fetch cs.subject s
            left join fetch bs.teacher t
            where e.active = true and e.dayOfWeek = :dayOfWeek
            order by e.startTime
            """)
    List<TimetableEntry> findActiveByDay(@Param("dayOfWeek") DayOfWeek dayOfWeek);

    /**
     * The schedules that could conflict with the given one: same
     * weekday, overlapping time range and overlapping effective period.
     * The teacher, the batch and the classroom are compared by the
     * service, which can read them from the assignment.
     */
    @Query("""
            select e from TimetableEntry e
            join fetch e.batchSubject bs
            join fetch bs.batch b
            left join fetch bs.teacher t
            where e.id <> :id
              and e.active = true
              and e.dayOfWeek = :dayOfWeek
              and e.startTime < :endTime
              and :startTime < e.endTime
              and e.effectiveFrom <= :effectiveUntil
              and (e.effectiveUntil is null or e.effectiveUntil >= :effectiveFrom)
            """)
    List<TimetableEntry> findPotentialConflicts(@Param("id") Long id,
                                                @Param("dayOfWeek") DayOfWeek dayOfWeek,
                                                @Param("startTime") LocalTime startTime,
                                                @Param("endTime") LocalTime endTime,
                                                @Param("effectiveFrom") LocalDate effectiveFrom,
                                                @Param("effectiveUntil") LocalDate effectiveUntil);

    /** The schedules of one teacher. */
    @Query("""
            select e from TimetableEntry e
            join fetch e.batchSubject bs
            join fetch bs.batch b
            join fetch b.course c
            join fetch bs.courseSubject cs
            join fetch cs.subject s
            left join fetch bs.teacher t
            where bs.teacher.id = :teacherId
            order by case e.dayOfWeek
                when java.time.DayOfWeek.MONDAY then 1
                when java.time.DayOfWeek.TUESDAY then 2
                when java.time.DayOfWeek.WEDNESDAY then 3
                when java.time.DayOfWeek.THURSDAY then 4
                when java.time.DayOfWeek.FRIDAY then 5
                when java.time.DayOfWeek.SATURDAY then 6
                when java.time.DayOfWeek.SUNDAY then 7
                else 8 end, e.startTime
            """)
    List<TimetableEntry> findForTeacher(@Param("teacherId") Long teacherId);

    /** The schedules of one batch. */
    @Query("""
            select e from TimetableEntry e
            join fetch e.batchSubject bs
            join fetch bs.batch b
            join fetch b.course c
            join fetch bs.courseSubject cs
            join fetch cs.subject s
            left join fetch bs.teacher t
            where bs.batch.id = :batchId
            order by case e.dayOfWeek
                when java.time.DayOfWeek.MONDAY then 1
                when java.time.DayOfWeek.TUESDAY then 2
                when java.time.DayOfWeek.WEDNESDAY then 3
                when java.time.DayOfWeek.THURSDAY then 4
                when java.time.DayOfWeek.FRIDAY then 5
                when java.time.DayOfWeek.SATURDAY then 6
                when java.time.DayOfWeek.SUNDAY then 7
                else 8 end, e.startTime
            """)
    List<TimetableEntry> findForBatch(@Param("batchId") Long batchId);

    /** The schedules of one classroom. */
    @Query("""
            select e from TimetableEntry e
            join fetch e.batchSubject bs
            join fetch bs.batch b
            join fetch b.course c
            join fetch bs.courseSubject cs
            join fetch cs.subject s
            left join fetch bs.teacher t
            where e.classroom = :classroom
            order by case e.dayOfWeek
                when java.time.DayOfWeek.MONDAY then 1
                when java.time.DayOfWeek.TUESDAY then 2
                when java.time.DayOfWeek.WEDNESDAY then 3
                when java.time.DayOfWeek.THURSDAY then 4
                when java.time.DayOfWeek.FRIDAY then 5
                when java.time.DayOfWeek.SATURDAY then 6
                when java.time.DayOfWeek.SUNDAY then 7
                else 8 end, e.startTime
            """)
    List<TimetableEntry> findForClassroom(@Param("classroom") String classroom);

    /** One entry with its assignment, batch, course, subject and teacher. */
    @Query("""
            select e from TimetableEntry e
            join fetch e.batchSubject bs
            join fetch bs.batch b
            join fetch b.course c
            join fetch bs.courseSubject cs
            join fetch cs.subject s
            left join fetch bs.teacher t
            join fetch e.createdBy u
            where e.id = :id
            """)
    Optional<TimetableEntry> findWithContextById(@Param("id") Long id);
}
