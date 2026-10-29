package com.omsai.coaching.student;

import com.omsai.coaching.batch.BatchSubject;
import com.omsai.coaching.batch.BatchSubjectRepository;
import com.omsai.coaching.common.BusinessRuleException;
import com.omsai.coaching.security.AppUserPrincipal;
import com.omsai.coaching.security.UserAccount;
import com.omsai.coaching.security.UserAccountRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * The staff quiz screens, shared by administrators and teachers.
 *
 * <p>A teacher manages the quizzes of their own assignments only; an
 * administrator manages everything. The service enforces the scope -
 * the controller only hands it the signed-in account.</p>
 */
@Controller
@RequestMapping("/quizzes")
@PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
public class QuizAuthoringController {

    private final QuizAuthoringService quizAuthoringService;
    private final QuizQuestionRepository questionRepository;
    private final BatchSubjectRepository batchSubjectRepository;
    private final UserAccountRepository userAccountRepository;

    public QuizAuthoringController(QuizAuthoringService quizAuthoringService,
                                   QuizQuestionRepository questionRepository,
                                   BatchSubjectRepository batchSubjectRepository,
                                   UserAccountRepository userAccountRepository) {
        this.quizAuthoringService = quizAuthoringService;
        this.questionRepository = questionRepository;
        this.batchSubjectRepository = batchSubjectRepository;
        this.userAccountRepository = userAccountRepository;
    }

    /** The quiz list: the administrator sees everything, teachers their own. */
    @GetMapping
    public String list(Model model) {
        UserAccount account = currentUser();
        boolean admin = account.getRole() == UserAccount.Role.ADMIN;
        model.addAttribute("staffAdmin", admin);
        model.addAttribute("quizzes", admin
                ? quizAuthoringService.forAdmin()
                : quizAuthoringService.forTeacher(account));
        model.addAttribute("questionCount", questionCountByQuiz());
        return "quizzes/list";
    }

    /** The create form: pick one assignment, title it, set the duration. */
    @GetMapping("/new")
    public String newForm(Model model) {
        UserAccount account = currentUser();
        model.addAttribute("staffAdmin", account.getRole() == UserAccount.Role.ADMIN);
        model.addAttribute("assignments", account.getRole() == UserAccount.Role.ADMIN
                ? batchSubjectRepository.findForActiveBatches()
                : batchSubjectRepository.findForUser(account.getId()));
        return "quizzes/form";
    }

    /** Create the empty quiz, then continue on the questions page. */
    @PostMapping
    public String create(@RequestParam Long batchSubjectId,
                         @RequestParam String title,
                         @RequestParam(required = false) String description,
                         @RequestParam String durationMinutes,
                         RedirectAttributes redirect) {
        UserAccount account = currentUser();
        int duration;
        try {
            duration = Integer.parseInt(durationMinutes == null ? ""
                    : durationMinutes.strip());
        } catch (NumberFormatException e) {
            redirect.addFlashAttribute("errorMessage",
                    "Enter the duration in whole minutes, for example 10.");
            return "redirect:/quizzes/new";
        }
        Quiz quiz;
        try {
            quiz = quizAuthoringService.create(batchSubjectId, title, description,
                    duration, account);
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/quizzes/new";
        }
        redirect.addFlashAttribute("successMessage",
                "Quiz created. Now add the questions.");
        return "redirect:/quizzes/" + quiz.getId();
    }

    /** The question manager of one quiz (shows the correct letters to staff). */
    @GetMapping("/{id}")
    public String manage(@PathVariable Long id, Model model,
                         RedirectAttributes redirect) {
        UserAccount account = currentUser();
        Quiz quiz;
        try {
            quiz = quizAuthoringService.managed(id, account);
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/quizzes";
        }
        model.addAttribute("quiz", quiz);
        model.addAttribute("questions", quizAuthoringService.questionsOf(quiz));
        return "quizzes/questions";
    }

    /** Add one question at the end of the quiz. */
    @PostMapping("/{id}/questions")
    public String addQuestion(@PathVariable Long id,
                              @RequestParam String questionText,
                              @RequestParam String optionA,
                              @RequestParam String optionB,
                              @RequestParam String optionC,
                              @RequestParam String optionD,
                              @RequestParam String correctOption,
                              RedirectAttributes redirect) {
        UserAccount account = currentUser();
        try {
            quizAuthoringService.addQuestion(id, questionText, optionA, optionB,
                    optionC, optionD, correctOption, account);
            redirect.addFlashAttribute("successMessage", "Question added.");
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/quizzes/" + id;
    }

    /** Remove one question and close the position gap. */
    @PostMapping("/{id}/questions/{questionId}/remove")
    public String removeQuestion(@PathVariable Long id,
                                 @PathVariable Long questionId,
                                 RedirectAttributes redirect) {
        UserAccount account = currentUser();
        try {
            quizAuthoringService.removeQuestion(id, questionId, account);
            redirect.addFlashAttribute("successMessage",
                    "Question removed; the numbering stays gapless.");
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/quizzes/" + id;
    }

    /** Question counts per quiz id, for the list page. */
    private java.util.Map<Long, Long> questionCountByQuiz() {
        java.util.Map<Long, Long> counts = new java.util.LinkedHashMap<>();
        for (Object[] row : questionRepository.countPerQuiz()) {
            counts.put((Long) row[0], (Long) row[1]);
        }
        return counts;
    }

    // ----- helpers ----------------------------------------------------------

    private UserAccount currentUser() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication != null
                && authentication.getPrincipal() instanceof AppUserPrincipal principal)) {
            throw new AccessDeniedException("Not authenticated.");
        }
        return userAccountRepository.findById(principal.id())
                .orElseThrow(() -> new AccessDeniedException("Your account no longer exists."));
    }
}
