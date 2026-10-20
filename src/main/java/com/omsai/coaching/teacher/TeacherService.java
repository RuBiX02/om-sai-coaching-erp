package com.omsai.coaching.teacher;

import com.omsai.coaching.batch.BatchSubject;
import com.omsai.coaching.batch.BatchSubjectRepository;
import com.omsai.coaching.common.BusinessRuleException;
import com.omsai.coaching.security.UserAccount;
import com.omsai.coaching.security.UserAccountRepository;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * Teacher management business rules. Teacher profiles and login
 * accounts are managed as related but separate concepts: a profile can
 * exist without a login, and a linked login must be a TEACHER-role
 * account that is not already linked to another profile.
 */
@Service
public class TeacherService {

    private final TeacherRepository teacherRepository;
    private final UserAccountRepository userRepository;
    private final BatchSubjectRepository batchSubjectRepository;

    public TeacherService(TeacherRepository teacherRepository,
                          UserAccountRepository userRepository,
                          BatchSubjectRepository batchSubjectRepository) {
        this.teacherRepository = teacherRepository;
        this.userRepository = userRepository;
        this.batchSubjectRepository = batchSubjectRepository;
    }

    @Transactional(readOnly = true)
    public Page<Teacher> search(String q, Boolean active, Pageable pageable) {
        String query = (q == null || q.isBlank()) ? "" : q.trim().toLowerCase();
        return teacherRepository.search(query, active, pageable);
    }

    /** TEACHER-role accounts without a profile, usable for linking. */
    @Transactional(readOnly = true)
    public List<UserAccount> linkableAccounts() {
        return userRepository
                .findUnlinkedTeachers(UserAccount.Role.TEACHER, PageRequest.of(0, 200))
                .getContent();
    }

    @Transactional(readOnly = true)
    public Teacher get(Long id) {
        return teacherRepository.findWithLinkedUser(id)
                .orElseThrow(() -> new BusinessRuleException("Teacher profile not found."));
    }

    @Transactional(readOnly = true)
    public List<Teacher> activeTeachers() {
        return teacherRepository.findActiveTeachers();
    }

    @Transactional
    public Teacher create(TeacherForm form) {
        UserAccount linkedUser = resolveLinkedUser(form.getUserId(), null);
        Teacher teacher = new Teacher();
        teacher.setFullName(form.getFullName().trim());
        teacher.setPhone(form.getPhone().trim());
        teacher.setEmail(clean(form.getEmail()));
        teacher.setLinkedUser(linkedUser);
        return teacherRepository.save(teacher);
    }

    @Transactional
    public Teacher update(Long id, TeacherForm form) {
        Teacher teacher = get(id);
        teacher.setFullName(form.getFullName().trim());
        teacher.setPhone(form.getPhone().trim());
        teacher.setEmail(clean(form.getEmail()));
        teacher.setLinkedUser(resolveLinkedUser(form.getUserId(), id));
        return teacherRepository.save(teacher);
    }

    @Transactional
    public Teacher setStatus(Long id, boolean active) {
        Teacher teacher = get(id);
        teacher.setActive(active);
        return teacherRepository.save(teacher);
    }

    /** Subjects and batches this teacher is assigned to teach. */
    @Transactional(readOnly = true)
    public List<BatchSubject> assignmentsOf(Teacher teacher) {
        return batchSubjectRepository.findForTeacher(teacher.getId());
    }

    @Transactional(readOnly = true)
    public List<BatchSubject> assignmentsForUser(Long userId) {
        return batchSubjectRepository.findForUser(userId);
    }

    @Transactional(readOnly = true)
    public Teacher findByUser(Long userId) {
        return teacherRepository.findByLinkedUserId(userId).orElse(null);
    }

    /**
     * Validates the account link: the user must exist, carry the TEACHER
     * role, and not already be linked to a different teacher profile.
     */
    private UserAccount resolveLinkedUser(Long userId, Long teacherId) {
        if (userId == null) {
            return null;
        }
        UserAccount user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessRuleException("Selected account does not exist."));
        if (user.getRole() != UserAccount.Role.TEACHER) {
            throw new BusinessRuleException("Only accounts with the TEACHER role can be linked to a teacher profile.");
        }
        teacherRepository.findByLinkedUserId(userId).ifPresent(linkedTeacher -> {
            if (!linkedTeacher.getId().equals(teacherId)) {
                throw new BusinessRuleException("That account is already linked to another teacher profile ("
                        + linkedTeacher.getFullName() + ").");
            }
        });
        return user;
    }

    private static String clean(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
