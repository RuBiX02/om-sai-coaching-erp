package com.omsai.coaching.teacher;

import com.omsai.coaching.batch.BatchSubject;
import com.omsai.coaching.security.UserAccount;
import com.omsai.coaching.security.UserAccountRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

/**
 * Read-only home view for TEACHER accounts showing the subjects and
 * batches they are assigned to teach. TEACHER accounts can reach only
 * this page; all management features stay ADMIN-only on the server.
 */
@Controller
@PreAuthorize("hasRole('TEACHER')")
public class TeacherHomeController {

    private final TeacherService teacherService;
    private final UserAccountRepository userRepository;

    public TeacherHomeController(TeacherService teacherService, UserAccountRepository userRepository) {
        this.teacherService = teacherService;
        this.userRepository = userRepository;
    }

    @GetMapping("/teacher")
    public String teacherHome(Authentication authentication, Model model) {
        model.addAttribute("displayName", authentication.getName());
        UserAccount user = userRepository.findByEmailIgnoreCase(authentication.getName()).orElse(null);
        Teacher profile = user == null ? null : teacherService.findByUser(user.getId());
        List<BatchSubject> assignments = profile == null ? List.of() : teacherService.assignmentsOf(profile);
        model.addAttribute("profile", profile);
        model.addAttribute("assignments", assignments);
        return "dashboard/teacher";
    }
}
