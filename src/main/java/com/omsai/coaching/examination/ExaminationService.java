package com.omsai.coaching.examination;

import com.omsai.coaching.attendance.SessionAuthority;
import com.omsai.coaching.batch.BatchSubject;
import com.omsai.coaching.batch.BatchSubjectRepository;
import com.omsai.coaching.common.BusinessClock;
import com.omsai.coaching.common.BusinessRuleException;
import com.omsai.coaching.enrollment.Enrollment;
import com.omsai.coaching.enrollment.EnrollmentRepository;
import com.omsai.coaching.security.UserAccount;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Examination and result rules.
 *
 * <p>An examination belongs to one batch-subject assignment, so it
 * always concerns a class somebody teaches: administrators may manage
 * every examination, a teacher only those of their own assignments.</p>
 *
 * <p>A result belongs to the student's <em>enrollment</em> - that is
 * what keeps the academic history intact when a student transfers to
 * another batch. The enrollment must be on the roster of the
 * examination's batch on the examination date: a student who enrolled
 * later was not there.</p>
 *
 * <p>Publishing is an administrator action. A teacher prepares the
 * examination and enters the marks; only an administrator publishes
 * them, which is what makes a result visible in the student portal. A
 * published result is corrected (audited), never deleted.</p>
 */
@Service
public class ExaminationService {

    private final ExaminationRepository examinationRepository;
    private final ExaminationResultRepository resultRepository;
    private final BatchSubjectRepository batchSubjectRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final SessionAuthority authority;
    private final BusinessClock businessClock;

    public ExaminationService(ExaminationRepository examinationRepository,
                              ExaminationResultRepository resultRepository,
                              BatchSubjectRepository batchSubjectRepository,
                              EnrollmentRepository enrollmentRepository,
                              SessionAuthority authority,
                              BusinessClock businessClock) {
        this.examinationRepository = examinationRepository;
        this.resultRepository = resultRepository;
        this.batchSubjectRepository = batchSubjectRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.authority = authority;
        this.businessClock = businessClock;
    }

    // ----- examinations ----------------------------------------------------

    /** Creates an examination for a class. */
    @Transactional
    public Examination create(ExaminationForm form, UserAccount user) {
        BatchSubject assignment = batchSubjectRepository.findById(form.getBatchSubjectId())
                .orElseThrow(() -> new BusinessRuleException("The class (batch subject) does not exist."));
        requireCanManage(assignment, user);
        validateMarks(form.getMaximumMarks());
        if (form.getExaminationDate() == null) {
            throw new BusinessRuleException("The examination date is required.");
        }
        if (form.getTitle() == null || form.getTitle().isBlank()) {
            throw new BusinessRuleException("The examination title is required.");
        }
        Examination examination = new Examination();
        examination.setBatchSubject(assignment);
        applyForm(examination, form);
        examination.setStatus(Examination.Status.DRAFT);
        examination.setCreatedBy(user);
        return examinationRepository.save(examination);
    }

    /** Updates a DRAFT examination. A published one is kept as it was. */
    @Transactional
    public Examination update(Long id, ExaminationForm form, UserAccount user) {
        Examination examination = get(id);
        requireCanManage(examination.getBatchSubject(), user);
        if (examination.getStatus() != Examination.Status.DRAFT) {
            throw new BusinessRuleException(
                    "Only a draft examination can be edited; this one is " + examination.getStatus() + ".");
        }
        validateMarks(form.getMaximumMarks());
        if (form.getExaminationDate() == null) {
            throw new BusinessRuleException("The examination date is required.");
        }
        applyForm(examination, form);
        return examinationRepository.save(examination);
    }

    /** Publishes an examination and its results (administrators only). */
    @Transactional
    public Examination publish(Long id, UserAccount user) {
        if (!authority.isAdmin(user)) {
            throw new BusinessRuleException("Only an administrator can publish an examination.");
        }
        Examination examination = get(id);
        if (examination.getStatus() == Examination.Status.CANCELLED) {
            throw new BusinessRuleException("A cancelled examination cannot be published.");
        }
        examination.setStatus(Examination.Status.PUBLISHED);
        examination.setPublishedBy(user);
        examination.setPublishedAt(businessClock.now());
        for (ExaminationResult result : resultRepository.findForExamination(id)) {
            result.setPublished(true);
            result.setPublishedBy(user);
            result.setPublishedAt(businessClock.now());
            resultRepository.save(result);
        }
        return examinationRepository.save(examination);
    }

    /** Cancels an examination; its results and their history are kept. */
    @Transactional
    public Examination cancel(Long id, UserAccount user) {
        Examination examination = get(id);
        requireCanManage(examination.getBatchSubject(), user);
        if (examination.getStatus() == Examination.Status.PUBLISHED) {
            throw new BusinessRuleException("A published examination cannot be cancelled.");
        }
        examination.setStatus(Examination.Status.CANCELLED);
        return examinationRepository.save(examination);
    }

    @Transactional(readOnly = true)
    public Examination get(Long id) {
        return examinationRepository.findWithDetails(id)
                .orElseThrow(() -> new BusinessRuleException("Examination not found."));
    }

    @Transactional(readOnly = true)
    public List<Examination> all() {
        return examinationRepository.findAllWithContext();
    }

    @Transactional(readOnly = true)
    public List<Examination> forTeacher(Long teacherId) {
        return examinationRepository.findForTeacher(teacherId);
    }

    // ----- marks -----------------------------------------------------------

    /** One result row of the marks-entry form. */
    public record ResultInput(Long enrollmentId, String status, String marks, String remarks) {
    }

    /**
     * The eligible roster of an examination: the enrollments of the
     * examination's batch whose effective period covers the
     * examination date.
     */
    @Transactional(readOnly = true)
    public List<Enrollment> rosterFor(Examination examination) {
        return enrollmentRepository.findRosterForBatchOnDate(
                examination.getBatchSubject().getBatch().getId(), examination.getExaminationDate());
    }

    /**
     * Records or corrects the results of an examination. Every row is
     * validated server side: the enrollment must belong to the
     * examination's batch and must have been on the roster on the
     * examination date, a present student needs a mark that does not
     * exceed the maximum, and an absent or excused student must not
     * have one.
     */
    @Transactional
    public int recordResults(Long examinationId, List<ResultInput> inputs, UserAccount user) {
        Examination examination = get(examinationId);
        requireCanManage(examination.getBatchSubject(), user);
        if (examination.getStatus() == Examination.Status.CANCELLED) {
            throw new BusinessRuleException("A cancelled examination has no results.");
        }
        if (examination.getStatus() == Examination.Status.PUBLISHED && !authority.isAdmin(user)) {
            throw new BusinessRuleException(
                    "The results of a published examination can only be corrected by an administrator.");
        }
        BigDecimal maximum = examination.getMaximumMarks();
        int saved = 0;
        for (ResultInput input : inputs) {
            if (input == null || input.enrollmentId() == null) {
                continue;
            }
            Enrollment enrollment = enrollmentRepository.findById(input.enrollmentId())
                    .orElseThrow(() -> new BusinessRuleException("The enrollment does not exist."));
            requireEligible(examination, enrollment);
            ExaminationResult.Status status = parseStatus(input.status());
            BigDecimal marks = parseMarks(input.marks(), status, maximum);

            ExaminationResult result = resultRepository
                    .findByExaminationIdAndEnrollmentId(examinationId, enrollment.getId())
                    .orElseGet(ExaminationResult::new);
            boolean correction = result.getId() != null;
            if (correction && result.isPublished() && !authority.isAdmin(user)) {
                throw new BusinessRuleException(
                        "The results of a published examination can only be corrected by an administrator.");
            }
            result.setExamination(examination);
            result.setEnrollment(enrollment);
            result.setStatus(status);
            result.setMarks(marks);
            result.setRemarks(input.remarks() == null || input.remarks().isBlank()
                    ? null : input.remarks().trim());
            result.setEnteredBy(user);
            if (correction) {
                result.setCorrectedBy(user);
                result.setCorrectedAt(businessClock.now());
            }
            resultRepository.save(result);
            saved++;
        }
        return saved;
    }

    /** The results of an examination, with the roster that has no result yet. */
    public record ResultRow(Enrollment enrollment, String studentCode, String studentName,
                             ExaminationResult result) {
    }

    @Transactional(readOnly = true)
    public List<ResultRow> resultRows(Examination examination) {
        List<ExaminationResult> results = resultRepository.findForExamination(examination.getId());
        List<ResultRow> rows = new ArrayList<>();
        for (Enrollment enrollment : rosterFor(examination)) {
            ExaminationResult result = results.stream()
                    .filter(candidate -> candidate.getEnrollment().getId().equals(enrollment.getId()))
                    .findFirst()
                    .orElse(null);
            rows.add(new ResultRow(enrollment, enrollment.getStudent().getStudentCode(),
                    enrollment.getStudent().getFullName(), result));
        }
        return rows;
    }

    @Transactional(readOnly = true)
    public List<ExaminationResult> resultsFor(Examination examination) {
        return resultRepository.findForExamination(examination.getId());
    }

    /** The published results of one enrollment (the student's own history). */
    @Transactional(readOnly = true)
    public List<ExaminationResult> publishedResultsFor(Long enrollmentId) {
        return resultRepository.findPublishedForEnrollment(enrollmentId);
    }

    /** The results of one enrollment, including the drafts (staff view). */
    @Transactional(readOnly = true)
    public List<ExaminationResult> resultsForEnrollment(Long enrollmentId) {
        return resultRepository.findForEnrollment(enrollmentId);
    }

    // ----- reports ---------------------------------------------------------

    /**
     * The summary of one examination: how many students sat, were
     * absent or were excused, the average and the highest mark of the
     * students who sat it. A student without a mark never lowers the
     * average - absent and excused results are counted separately.
     */
    public record ExamSummary(Examination examination,
                              long presentCount,
                              long absentCount,
                              long excusedCount,
                              BigDecimal averageMarks,
                              BigDecimal highestMarks) {
    }

    /** The summaries of the given examinations. */
    @Transactional(readOnly = true)
    public List<ExamSummary> summariesFor(List<Examination> examinations) {
        List<ExamSummary> summaries = new ArrayList<>();
        for (Examination examination : examinations) {
            long present = 0;
            long absent = 0;
            long excused = 0;
            BigDecimal total = BigDecimal.ZERO;
            BigDecimal highest = null;
            for (ExaminationResult result : resultRepository.findForExamination(examination.getId())) {
                switch (result.getStatus()) {
                    case ABSENT -> absent++;
                    case EXCUSED -> excused++;
                    case PRESENT -> {
                        present++;
                        if (result.getMarks() != null) {
                            total = total.add(result.getMarks());
                            if (highest == null || result.getMarks().compareTo(highest) > 0) {
                                highest = result.getMarks();
                            }
                        }
                    }
                }
            }
            BigDecimal average = present == 0 ? null
                    : total.divide(new BigDecimal(present), 2, RoundingMode.HALF_UP);
            summaries.add(new ExamSummary(examination, present, absent, excused, average, highest));
        }
        return summaries;
    }

    /** The examinations of one batch in a date range. */
    @Transactional(readOnly = true)
    public List<Examination> forBatch(Long batchId, LocalDate from, LocalDate to) {
        return examinationRepository.findForBatch(batchId, from, to);
    }

    // ----- helpers ---------------------------------------------------------

    private void requireCanManage(BatchSubject assignment, UserAccount user) {
        if (authority.isAdmin(user)) {
            return;
        }
        authority.requireCanManage(assignment, user);
    }

    /**
     * The enrollment must belong to the examination's batch and must
     * have been on the roster on the examination date.
     */
    private void requireEligible(Examination examination, Enrollment enrollment) {
        if (!enrollment.getBatch().getId().equals(examination.getBatchSubject().getBatch().getId())) {
            throw new BusinessRuleException(enrollment.getStudent().getFullName()
                    + " is not enrolled in this batch.");
        }
        LocalDate date = examination.getExaminationDate();
        if (enrollment.getEnrollmentDate().isAfter(date)) {
            throw new BusinessRuleException(enrollment.getStudent().getFullName()
                    + " enrolled only on " + enrollment.getEnrollmentDate()
                    + " and could not sit an examination of " + date + ".");
        }
        if (enrollment.getEndDate() != null && enrollment.getEndDate().isBefore(date)) {
            throw new BusinessRuleException(enrollment.getStudent().getFullName()
                    + " left the batch on " + enrollment.getEndDate()
                    + " and could not sit an examination of " + date + ".");
        }
    }

    private static void validateMarks(BigDecimal maximumMarks) {
        if (maximumMarks == null) {
            throw new BusinessRuleException("The maximum marks are required.");
        }
        if (maximumMarks.signum() <= 0) {
            throw new BusinessRuleException("The maximum marks must be greater than zero.");
        }
        if (maximumMarks.scale() > 2) {
            throw new BusinessRuleException("The maximum marks must have at most two decimal places.");
        }
        if (maximumMarks.precision() - maximumMarks.scale() > 6) {
            throw new BusinessRuleException("The maximum marks are too large.");
        }
    }

    private static ExaminationResult.Status parseStatus(String text) {
        if (text == null || text.isBlank()) {
            throw new BusinessRuleException("Please choose a status for every student.");
        }
        try {
            return ExaminationResult.Status.valueOf(text.trim());
        } catch (IllegalArgumentException e) {
            throw new BusinessRuleException("\"" + text.trim() + "\" is not a valid status.");
        }
    }

    private static BigDecimal parseMarks(String text, ExaminationResult.Status status, BigDecimal maximum) {
        boolean given = text != null && !text.isBlank();
        if (status != ExaminationResult.Status.PRESENT) {
            if (given) {
                throw new BusinessRuleException("An absent or excused student must not have a mark.");
            }
            return null;
        }
        if (!given) {
            throw new BusinessRuleException("A present student needs a mark.");
        }
        BigDecimal marks;
        try {
            marks = new BigDecimal(text.trim());
        } catch (NumberFormatException e) {
            throw new BusinessRuleException("\"" + text.trim() + "\" is not a valid mark.");
        }
        if (marks.signum() < 0) {
            throw new BusinessRuleException("A mark cannot be negative.");
        }
        if (marks.scale() > 2) {
            throw new BusinessRuleException("A mark must have at most two decimal places.");
        }
        if (marks.compareTo(maximum) > 0) {
            throw new BusinessRuleException("The mark " + marks + " is above the maximum of " + maximum + ".");
        }
        return marks.setScale(2, RoundingMode.UNNECESSARY);
    }

    private static void applyForm(Examination examination, ExaminationForm form) {
        examination.setTitle(form.getTitle() == null ? null : form.getTitle().trim());
        examination.setDescription(form.getDescription() == null || form.getDescription().isBlank()
                ? null : form.getDescription().trim());
        examination.setExaminationDate(form.getExaminationDate());
        examination.setMaximumMarks(form.getMaximumMarks());
    }
}
