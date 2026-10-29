package com.omsai.coaching.student;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface StudyResourceRepository extends JpaRepository<StudyResource, Long> {

    /**
     * The resources a batch sees: everything for the institute-wide
     * subjects it studies plus everything tied to the batch itself,
     * subject then title order.
     */
    @Query("""
            select r from StudyResource r
            where r.batch.id = :batchId
               or (r.batch is null and r.subject.id in :subjectIds)
            order by r.subject.name, r.resourceType, r.title
            """)
    List<StudyResource> visibleTo(@Param("batchId") Long batchId,
                                  @Param("subjectIds") List<Long> subjectIds);
}
