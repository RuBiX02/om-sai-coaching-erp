package com.omsai.coaching.dashboard;

import com.omsai.coaching.common.BusinessClock;
import com.omsai.coaching.fees.InstallmentService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Administrator dashboard with live database figures.
 *
 * <p>Every figure on the page comes from the service layer; the
 * controller only decides what is shown and adds the day-dependent
 * presentation bits (the greeting and today's date) taken from the
 * business clock, so the screen agrees with the institute's
 * calendar.</p>
 */
@Controller
@PreAuthorize("hasRole('ADMIN')")
public class DashboardController {

    private final DashboardService dashboardService;
    private final InstallmentService installmentService;
    private final BusinessClock businessClock;

    public DashboardController(DashboardService dashboardService,
                               InstallmentService installmentService,
                               BusinessClock businessClock) {
        this.dashboardService = dashboardService;
        this.installmentService = installmentService;
        this.businessClock = businessClock;
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        model.addAttribute("stats", dashboardService.stats());
        model.addAttribute("recentEnrollments", dashboardService.recentEnrollments());
        model.addAttribute("phase2", dashboardService.phaseTwoStats());
        model.addAttribute("sessionsToday", dashboardService.sessionsToday());
        model.addAttribute("installments", installmentService.overview());
        model.addAttribute("nextInstallments", installmentService.nextDue(5));
        model.addAttribute("today", businessClock.today());
        model.addAttribute("greeting", greeting());
        return "dashboard/dashboard";
    }

    /**
     * The greeting follows the business-timezone clock, so it is
     * correct at any hour of the day and never stale.
     */
    private String greeting() {
        int hour = businessClock.nowLocal().getHour();
        if (hour < 12) {
            return "Good morning";
        }
        if (hour < 17) {
            return "Good afternoon";
        }
        return "Good evening";
    }
}
