package com.omsai.coaching.notice;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface AnnouncementRepository extends JpaRepository<Announcement, Long> {

    /**
     * The office list: everything, newest first (withdrawn stays visible).
     * The publisher and the batch are fetched here because the page renders
     * them after the transaction has closed (open-in-view is off).
     */
    @Query("""
            select a from Announcement a
            join fetch a.publishedBy p
            left join fetch a.batch b
            order by a.publishedAt desc, a.id desc
            """)
    List<Announcement> findAllWithContext();

    /**
     * The notices a student may see: active ones addressed to everybody
     * or to one of their batches, newest first.
     */
    @Query("""
            select a from Announcement a
            join fetch a.publishedBy p
            left join fetch a.batch b
            where a.active = true
              and (a.audience = com.omsai.coaching.notice.Announcement.Audience.ALL
                   or b.id in :batchIds)
            order by a.publishedAt desc, a.id desc
            """)
    List<Announcement> forStudent(@Param("batchIds") Collection<Long> batchIds);

    /**
     * Same visibility when the student has no active batches: then only
     * the everyone-notices remain (an empty IN list must never be built).
     */
    @Query("""
            select a from Announcement a
            join fetch a.publishedBy p
            where a.active = true
              and a.audience = com.omsai.coaching.notice.Announcement.Audience.ALL
            order by a.publishedAt desc, a.id desc
            """)
    List<Announcement> forEveryone();
}
