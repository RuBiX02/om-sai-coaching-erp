package com.omsai.coaching.fees;

import com.omsai.coaching.common.BusinessClock;
import com.omsai.coaching.course.CourseRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;

/**
 * The installment management screens: the collection overview, the
 * upcoming installments and the overdue ones.
 *
 * <p>Administrator only, like every other financial screen: the URL
 * rule of SecurityConfig and the class-level check both apply, so a
 * teacher is turned away by the server, not by a hidden link.</p>
 */
@Controller
@RequestMapping("/installments")
@PreAuthorize("hasRole('ADMIN')")
public class InstallmentController {

    private final InstallmentService installmentService;
    private final CourseRepository courseRepository;
    private final BusinessClock businessClock;

    public InstallmentController(InstallmentService installmentService,
                                 CourseRepository courseRepository,
                                 BusinessClock businessClock) {
        this.installmentService = installmentService;
        this.courseRepository = courseRepository;
        this.businessClock = businessClock;
    }

    /** The collection overview of the installment module. */
    @GetMapping
    public String overview(Model model) {
        model.addAttribute("overview", installmentService.overview());
        model.addAttribute("nextDue", installmentService.nextDue(5));
        model.addAttribute("today", businessClock.today());
        return "installments/overview";
    }

    /** Installments with a balance whose due date is inside a range. */
    @GetMapping("/upcoming")
    public String upcoming(@RequestParam(required = false) LocalDate from,
                           @RequestParam(required = false) LocalDate to,
                           @RequestParam(required = false) String q,
                           @RequestParam(required = false) Long courseId,
                           @RequestParam(required = false) String academicYear,
                           @RequestParam(defaultValue = "0") int page,
                           Model model) {
        LocalDate today = businessClock.today();
        LocalDate effectiveFrom = from == null ? today : from;
        LocalDate effectiveTo = to == null ? today.plusDays(30) : to;
        model.addAttribute("rows",
                installmentService.upcoming(effectiveFrom, effectiveTo, q, courseId, academicYear,
                        PageRequest.of(page, 20)));
        model.addAttribute("courses", courseRepository.findAll());
        model.addAttribute("q", q);
        model.addAttribute("courseId", courseId);
        model.addAttribute("academicYear", academicYear);
        model.addAttribute("from", effectiveFrom);
        model.addAttribute("to", effectiveTo);
        model.addAttribute("today", today);
        model.addAttribute("pagerUrl", pagerUrl("/installments/upcoming", q, courseId, academicYear,
                effectiveFrom, effectiveTo));
        return "installments/upcoming";
    }

    /** Installments whose due date has passed and whose balance is positive. */
    @GetMapping("/overdue")
    public String overdue(@RequestParam(required = false) String q,
                          @RequestParam(required = false) Long courseId,
                          @RequestParam(required = false) String academicYear,
                          @RequestParam(defaultValue = "0") int page,
                          Model model) {
        model.addAttribute("rows",
                installmentService.overdue(q, courseId, academicYear, PageRequest.of(page, 20)));
        model.addAttribute("courses", courseRepository.findAll());
        model.addAttribute("q", q);
        model.addAttribute("courseId", courseId);
        model.addAttribute("academicYear", academicYear);
        model.addAttribute("today", businessClock.today());
        model.addAttribute("pagerUrl", pagerUrl("/installments/overdue", q, courseId, academicYear, null, null));
        return "installments/overdue";
    }

    // ----- helpers ----------------------------------------------------------

    private static String pagerUrl(String base, String q, Long courseId, String academicYear,
                                   LocalDate from, LocalDate to) {
        StringBuilder url = new StringBuilder(base);
        boolean first = true;
        if (q != null && !q.isBlank()) {
            url.append("?q=").append(q);
            first = false;
        }
        if (courseId != null) {
            url.append(first ? "?" : "&").append("courseId=").append(courseId);
            first = false;
        }
        if (academicYear != null && !academicYear.isBlank()) {
            url.append(first ? "?" : "&").append("academicYear=").append(academicYear);
            first = false;
        }
        if (from != null) {
            url.append(first ? "?" : "&").append("from=").append(from);
            first = false;
        }
        if (to != null) {
            url.append(first ? "?" : "&").append("to=").append(to);
        }
        return url.toString();
    }
}
