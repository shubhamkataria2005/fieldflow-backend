package com.Shubham.carDealership.fsm.controller;

import com.Shubham.carDealership.config.JwtUtil;
import com.Shubham.carDealership.fsm.dto.DashboardStats;
import com.Shubham.carDealership.fsm.repository.FsmInvoiceRepository;
import com.Shubham.carDealership.fsm.repository.FsmJobRepository;
import com.Shubham.carDealership.fsm.repository.FsmTechnicianRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/fsm/dashboard")
public class FsmDashboardController {

    @Autowired private FsmJobRepository jobRepo;
    @Autowired private FsmTechnicianRepository techRepo;
    @Autowired private FsmInvoiceRepository invoiceRepo;
    @Autowired private JwtUtil jwtUtil;

    private Long ownerId(HttpServletRequest req) {
        String h = req.getHeader("Authorization");
        if (h != null && h.startsWith("Bearer ")) {
            String token = h.substring(7);
            if (jwtUtil.validateToken(token)) return jwtUtil.extractUserId(token);
        }
        return null;
    }

    @GetMapping("/stats")
    public ResponseEntity<?> stats(HttpServletRequest req) {
        Long owner = ownerId(req);
        if (owner == null) return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));

        LocalDateTime todayStart = LocalDateTime.now().toLocalDate().atStartOfDay();
        LocalDateTime todayEnd = todayStart.plusDays(1);
        LocalDateTime monthStart = LocalDateTime.now().withDayOfMonth(1).toLocalDate().atStartOfDay();
        LocalDateTime monthEnd = LocalDateTime.now().toLocalDate().atStartOfDay().plusDays(1);
        LocalDateTime prevMonthStart = monthStart.minusMonths(1);
        LocalDateTime prevMonthEnd = monthStart;

        DashboardStats stats = new DashboardStats();

        // Today's jobs by count (scheduled today)
        long todayJobs = jobRepo.findByBusinessOwnerIdAndScheduledAtBetweenOrderByScheduledAtAsc(owner, todayStart, todayEnd).size();
        stats.setJobsToday(todayJobs);

        // Jobs by status
        stats.setJobsInProgress(jobRepo.countByBusinessOwnerIdAndStatus(owner, "IN_PROGRESS"));
        stats.setJobsCompleted(jobRepo.countByBusinessOwnerIdAndStatus(owner, "COMPLETED"));
        stats.setUnassignedJobs(jobRepo.countByBusinessOwnerIdAndTechnicianIsNull(owner));

        // Revenue
        BigDecimal revToday = jobRepo.sumRevenueByOwnerAndDateRange(owner, todayStart, todayEnd);
        stats.setRevenueToday(revToday != null ? revToday : BigDecimal.ZERO);

        BigDecimal revMonth = jobRepo.sumRevenueByOwnerAndDateRange(owner, monthStart, monthEnd);
        stats.setRevenueMonth(revMonth != null ? revMonth : BigDecimal.ZERO);

        BigDecimal revPrev = jobRepo.sumRevenueByOwnerAndDateRange(owner, prevMonthStart, prevMonthEnd);
        stats.setRevenuePrevMonth(revPrev != null ? revPrev : BigDecimal.ZERO);

        // Jobs this month
        long jobsMonth = jobRepo.findByBusinessOwnerIdAndScheduledAtBetweenOrderByScheduledAtAsc(owner, monthStart, monthEnd).size();
        stats.setJobsMonth(jobsMonth);

        // Technicians
        stats.setTotalTechnicians(techRepo.countByBusinessOwnerIdAndStatusNot(owner, "DELETED"));
        stats.setActiveTechnicians(techRepo.countByBusinessOwnerIdAndStatusNot(owner, "OFF"));

        // Outstanding invoices (UNPAID + OVERDUE)
        BigDecimal unpaid = invoiceRepo.sumByOwnerAndStatus(owner, "UNPAID");
        BigDecimal overdue = invoiceRepo.sumByOwnerAndStatus(owner, "OVERDUE");
        stats.setOutstandingInvoices(
                (unpaid != null ? unpaid : BigDecimal.ZERO).add(overdue != null ? overdue : BigDecimal.ZERO)
        );

        return ResponseEntity.ok(stats);
    }
}
