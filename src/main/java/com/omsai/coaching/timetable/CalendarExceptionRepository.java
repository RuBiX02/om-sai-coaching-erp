package com.omsai.coaching.timetable;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface CalendarExceptionRepository extends JpaRepository<CalendarException, Long> {

    /** The exceptions of one date. */
    @Query("""
            select e from CalendarException e
            left join fetch e.timetableEntry te
            join fetch e.createdBy u
            where e.exceptionDate = :date
            order by e.kind, e.id
            """)
    List<CalendarException> findForDate(@Param("date") LocalDate date);

    /** Every exception of a date range, newest first. */
    @Query("""
            select e from CalendarException e
            left join fetch e.timetableEntry te
            join fetch e.createdBy u
            where e.exceptionDate between :from and :to
            order by e.exceptionDate desc, e.id desc
            """)
    List<CalendarException> findBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /** The institute holidays of a date range. */
    @Query("""
            select e from CalendarException e
            join fetch e.createdBy u
            where e.kind = 'HOLIDAY' and e.exceptionDate between :from and :to
            order by e.exceptionDate
            """)
    List<CalendarException> findHolidays(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /** True when the institute is closed on that date. */
    @Query("""
            select count(e) from CalendarException e
            where e.kind = 'HOLIDAY' and e.exceptionDate = :date
            """)
    long countHolidays(@Param("date") LocalDate date);

    /** True when the given occurrence is cancelled. */
    @Query("""
            select count(e) from CalendarException e
            where e.kind = 'CANCELLED' and e.exceptionDate = :date
              and e.timetableEntry.id = :entryId
            """)
    long countCancellations(@Param("date") LocalDate date, @Param("entryId") Long entryId);
}
