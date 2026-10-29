package com.omsai.coaching.notice;

import com.omsai.coaching.batch.Batch;
import com.omsai.coaching.batch.BatchRepository;
import com.omsai.coaching.common.BusinessRuleException;
import com.omsai.coaching.security.UserAccount;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The notice board. The office publishes a notice to everybody or to
 * one batch; the student portal shows exactly the notices addressed to
 * the student and counts honestly what they have not read yet (the
 * read marks live in {@code announcement_reads}, written here).
 */
@Service
public class NoticeService {

    private final AnnouncementRepository announcementRepository;
    private final BatchRepository batchRepository;
    private final NamedParameterJdbcTemplate jdbc;

    public NoticeService(AnnouncementRepository announcementRepository,
                         BatchRepository batchRepository,
                         NamedParameterJdbcTemplate jdbc) {
        this.announcementRepository = announcementRepository;
        this.batchRepository = batchRepository;
        this.jdbc = jdbc;
    }

    // ----- the office side --------------------------------------------------

    /** Everything that was ever published, newest first. */
    @Transactional(readOnly = true)
    public List<Announcement> forOffice() {
        return announcementRepository.findAllWithContext();
    }

    /**
     * Publish a notice. An ALL notice must not name a batch; a BATCH
     * notice must name one. The publisher is the signed-in account.
     */
    @Transactional
    public Announcement publish(String title, String body, String audienceText,
                                Long batchId, UserAccount publisher) {
        String cleanTitle = requireBetween(title, 3, 160, "The title");
        String cleanBody = requireBetween(body, 10, 5000, "The notice text");

        Announcement.Audience audience = switch (audienceText == null ? ""
                : audienceText.trim().toUpperCase(Locale.ROOT)) {
            case "ALL" -> Announcement.Audience.ALL;
            case "BATCH" -> Announcement.Audience.BATCH;
            default -> throw new BusinessRuleException(
                    "Choose who the notice is for: everybody or one batch.");
        };

        Batch batch = null;
        if (audience == Announcement.Audience.BATCH) {
            if (batchId == null) {
                throw new BusinessRuleException(
                        "Pick the batch this notice addresses, or choose Everybody.");
            }
            batch = batchRepository.findById(batchId)
                    .orElseThrow(() -> new BusinessRuleException(
                            "That batch does not exist any more."));
        }

        Announcement notice = new Announcement();
        notice.setTitle(cleanTitle);
        notice.setBody(cleanBody);
        notice.setAudience(audience);
        notice.setBatch(batch);
        notice.setPublishedBy(publisher);
        return announcementRepository.save(notice);
    }

    /**
     * A published notice disappears from the student portal, but the
     * office keeps the record. Withdrawing twice is harmless.
     */
    @Transactional
    public void withdraw(Long id) {
        Announcement notice = announcementRepository.findById(id)
                .orElseThrow(() -> new BusinessRuleException(
                        "That notice does not exist any more."));
        notice.setActive(false);
        announcementRepository.save(notice);
    }

    // ----- the student side -------------------------------------------------

    /** The active notices a student's batches are addressed by, newest first. */
    @Transactional(readOnly = true)
    public List<Announcement> forStudent(Collection<Long> batchIds) {
        return batchIds.isEmpty()
                ? announcementRepository.forEveryone()
                : announcementRepository.forStudent(batchIds);
    }

    /** How many visible notices the account has not read yet (the badge). */
    @Transactional(readOnly = true)
    public int unreadCount(Collection<Long> batchIds, long userId) {
        return unreadIds(batchIds, userId).size();
    }

    /** The ids of the visible notices this account has not read. */
    @Transactional(readOnly = true)
    public Set<Long> unreadIds(Collection<Long> batchIds, long userId) {
        String visible = batchIds.isEmpty()
                ? "(a.audience = 'ALL')"
                : "((a.audience = 'ALL')"
                    + " OR (a.audience = 'BATCH' AND a.batch_id IN (:batchIds)))";
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("batchIds", batchIds)
                .addValue("userId", userId);
        String visibility = """
                SELECT a.id FROM announcements a
                WHERE a.active = TRUE AND %s
                  AND NOT EXISTS (SELECT 1 FROM announcement_reads r
                                   WHERE r.announcement_id = a.id
                                     AND r.user_account_id = :userId)
                """.formatted(visible);
        return new LinkedHashSet<>(jdbc.queryForList(visibility, params, Long.class));
    }

    /**
     * Mark every listed notice as read by this account. Inserting a
     * mark that already exists is harmless (the table's primary key
     * makes it idempotent).
     */
    @Transactional
    public void markRead(Collection<Long> announcementIds, long userId) {
        if (announcementIds.isEmpty()) {
            return;
        }
        MapSqlParameterSource[] rows = announcementIds.stream()
                .map(id -> new MapSqlParameterSource()
                        .addValue("announcementId", id)
                        .addValue("userId", userId))
                .toArray(MapSqlParameterSource[]::new);
        jdbc.batchUpdate("""
                INSERT INTO announcement_reads (announcement_id, user_account_id)
                VALUES (:announcementId, :userId)
                ON CONFLICT DO NOTHING
                """, rows);
    }

    // ----- helpers ----------------------------------------------------------

    private static String requireBetween(String value, int min, int max, String what) {
        String clean = value == null ? "" : value.strip();
        if (clean.length() < min) {
            throw new BusinessRuleException(what + " needs at least " + min + " characters.");
        }
        if (clean.length() > max) {
            throw new BusinessRuleException(what + " may be at most " + max + " characters.");
        }
        return clean;
    }
}
