package com.omsai.coaching.teacher;

import com.omsai.coaching.common.BusinessClock;
import com.omsai.coaching.security.UserAccount;
import com.omsai.coaching.security.UserAccountRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

/**
 * The teacher dashboard: today's classes, the classes that still need
 * attendance, the examinations of the teacher's own classes and the
 * teacher's schedule. A teacher without a linked profile or without
 * classes gets a useful empty page instead of an error.
 */
@Controller
@PreAuthorize("hasRole('TEACHER')")
public class TeacherHomeController {

    private final TeacherService teacherService;
    private final TeacherDashboardService dashboardService;
    private final UserAccountRepository userRepository;
    private final BusinessClock businessClock;

    public TeacherHomeController(TeacherService teacherService,
                                 TeacherDashboardService dashboardService,
                                 UserAccountRepository userRepository,
                                 BusinessClock businessClock) {
        this.teacherService = teacherService;
        this.dashboardService = dashboardService;
        this.userRepository = userRepository;
        this.businessClock = businessClock;
    }

    @GetMapping("/teacher")
    public String teacherHome(Authentication authentication, Model model) {
        model.addAttribute("displayName", authentication.getName());
        UserAccount user = userRepository.findByEmailIgnoreCase(authentication.getName()).orElse(null);
        Teacher profile = user == null ? null : teacherService.findByUser(user.getId());
        List<com.omsai.coaching.batch.BatchSubject> assignments = profile == null
                ? List.of() : teacherService.assignmentsOf(profile);
        model.addAttribute("profile", profile);
        model.addAttribute("assignments", assignments);
        model.addAttribute("today", businessClock.today());

        if (profile != null) {
            model.addAttribute("todayClasses", dashboardService.classesOn(profile.getId(), businessClock.today()));
            model.addAttribute("schedule", dashboardService.weeklySchedule(profile.getId()));
            model.addAttribute("upcomingSessions", dashboardService.upcomingSessions(profile.getId()));
            model.addAttribute("recentSessions", dashboardService.recentSessions(profile.getId()));
            model.addAttribute("attendancePending", dashboardService.attendancePending(profile.getId()));
            model.addAttribute("examinations", dashboardService.examinations(profile.getId()));
        } else {
            model.addAttribute("todayClasses", List.of());
            model.addAttribute("schedule", List.of());
            model.addAttribute("upcomingSessions", List.of());
            model.addAttribute("recentSessions", List.of());
            model.addAttribute("attendancePending", List.of());
            model.addAttribute("examinations", List.of());
        }
        return "dashboard/teacher";
    }
}
