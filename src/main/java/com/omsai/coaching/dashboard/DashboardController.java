package com.omsai.coaching.dashboard;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Administrator dashboard with live database figures.
 */
@Controller
@PreAuthorize("hasRole('ADMIN')")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        model.addAttribute("stats", dashboardService.stats());
        model.addAttribute("recentEnrollments", dashboardService.recentEnrollments());
        model.addAttribute("phase2", dashboardService.phaseTwoStats());
        model.addAttribute("sessionsToday", dashboardService.sessionsToday());
        return "dashboard/dashboard";
    }
}
