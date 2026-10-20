package com.omsai.coaching.batch;

import com.omsai.coaching.course.Course;
import com.omsai.coaching.subject.Subject;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CourseSubjectRepository extends JpaRepository<CourseSubject, Long> {

    boolean existsByCourseIdAndSubjectId(Long courseId, Long subjectId);

    /** Number of batches that currently teach this course subject. */
    @Query("""
            select count(bs) from BatchSubject bs
            where bs.courseSubject.id = :courseSubjectId
            """)
    long countAssignments(@Param("courseSubjectId") Long courseSubjectId);

    /** Number of batches across all courses that teach this subject. */
    @Query("""
            select count(bs) from BatchSubject bs
            where bs.courseSubject.subject.id = :subjectId
            """)
    long countAssignmentsForSubject(@Param("subjectId") Long subjectId);

    @Query("""
            select cs from CourseSubject cs
            join fetch cs.subject s
            where cs.course.id = :courseId
            order by s.name
            """)
    List<CourseSubject> findForCourse(@Param("courseId") Long courseId);

    @Query("""
            select cs from CourseSubject cs
            join fetch cs.course c
            where cs.subject.id = :subjectId
            order by c.name
            """)
    List<CourseSubject> findForSubject(@Param("subjectId") Long subjectId);

    /** Subjects of the course that are not yet assigned to it. */
    @Query("""
            select s from Subject s
            where not exists (
                select cs from CourseSubject cs
                where cs.course = :course and cs.subject = s)
            order by s.name
            """)
    List<Subject> findUnassignedSubjects(@Param("course") Course course);
}
