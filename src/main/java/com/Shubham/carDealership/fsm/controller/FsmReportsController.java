package com.Shubham.carDealership.fsm.controller;

import com.Shubham.carDealership.config.JwtUtil;
import com.Shubham.carDealership.fsm.model.FsmInvoice;
import com.Shubham.carDealership.fsm.model.FsmJob;
import com.Shubham.carDealership.fsm.repository.FsmCustomerRepository;
import com.Shubham.carDealership.fsm.repository.FsmInvoiceRepository;
import com.Shubham.carDealership.fsm.repository.FsmJobRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/fsm/reports")
public class FsmReportsController {

    @Autowired private FsmJobRepository      jobRepo;
    @Autowired private FsmInvoiceRepository  invoiceRepo;
    @Autowired private FsmCustomerRepository customerRepo;
    @Autowired private JwtUtil               jwtUtil;

    private Long ownerId(HttpServletRequest req) {
        String h = req.getHeader("Authorization");
        if (h != null && h.startsWith("Bearer ")) {
            String token = h.substring(7);
            if (jwtUtil.validateToken(token)) return jwtUtil.extractUserId(token);
        }
        return null;
    }

    // ── KPI overview ─────────────────────────────────────────────────────────
    @GetMapping("/overview")
    public ResponseEntity<?> overview(HttpServletRequest req) {
        Long owner = ownerId(req);
        if (owner == null) return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));

        List<FsmJob> allJobs = jobRepo.findByBusinessOwnerIdOrderByScheduledAtDesc(owner);

        BigDecimal totalRevenue  = invoiceRepo.sumByOwnerAndStatus(owner, "PAID");
        BigDecimal outstanding   = invoiceRepo.sumByOwnerAndStatus(owner, "UNPAID");
        BigDecimal overdue       = invoiceRepo.sumByOwnerAndStatus(owner, "OVERDUE");
        long completedJobs = allJobs.stream().filter(j -> "COMPLETED".equals(j.getStatus()) || "INVOICED".equals(j.getStatus())).count();
        long totalCustomers = customerRepo.countByBusinessOwnerId(owner);

        return ResponseEntity.ok(Map.of(
            "totalRevenue",    totalRevenue   != null ? totalRevenue   : BigDecimal.ZERO,
            "outstanding",     outstanding    != null ? outstanding    : BigDecimal.ZERO,
            "overdue",         overdue        != null ? overdue        : BigDecimal.ZERO,
            "totalJobs",       (long) allJobs.size(),
            "completedJobs",   completedJobs,
            "totalCustomers",  totalCustomers
        ));
    }

    // ── Jobs created per month (last 6 months) ────────────────────────────────
    @GetMapping("/jobs-by-month")
    public ResponseEntity<?> jobsByMonth(HttpServletRequest req) {
        Long owner = ownerId(req);
        if (owner == null) return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));

        LocalDateTime now  = LocalDateTime.now();
        LocalDateTime from = now.minusMonths(5).withDayOfMonth(1).toLocalDate().atStartOfDay();

        List<FsmJob> jobs = jobRepo.findByBusinessOwnerIdAndCreatedAtBetween(owner, from, now);

        // Build ordered list of last 6 months
        List<Map<String, Object>> result = new ArrayList<>();
        for (int i = 5; i >= 0; i--) {
            LocalDateTime monthStart = now.minusMonths(i).withDayOfMonth(1).toLocalDate().atStartOfDay();
            LocalDateTime monthEnd   = monthStart.plusMonths(1);
            Month month = monthStart.getMonth();
            String label = month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH) + " " + monthStart.getYear();
            long count = jobs.stream()
                .filter(j -> j.getCreatedAt() != null
                    && !j.getCreatedAt().isBefore(monthStart)
                    &&  j.getCreatedAt().isBefore(monthEnd))
                .count();
            result.add(Map.of("month", label, "count", count));
        }
        return ResponseEntity.ok(result);
    }

    // ── Revenue from PAID invoices per month (last 6 months) ─────────────────
    @GetMapping("/revenue-by-month")
    public ResponseEntity<?> revenueByMonth(HttpServletRequest req) {
        Long owner = ownerId(req);
        if (owner == null) return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));

        LocalDateTime now  = LocalDateTime.now();
        LocalDateTime from = now.minusMonths(5).withDayOfMonth(1).toLocalDate().atStartOfDay();

        List<FsmInvoice> paid = invoiceRepo.findByBusinessOwnerIdAndStatusAndIssuedAtBetween(owner, "PAID", from, now);

        List<Map<String, Object>> result = new ArrayList<>();
        for (int i = 5; i >= 0; i--) {
            LocalDateTime monthStart = now.minusMonths(i).withDayOfMonth(1).toLocalDate().atStartOfDay();
            LocalDateTime monthEnd   = monthStart.plusMonths(1);
            String label = monthStart.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH) + " " + monthStart.getYear();
            BigDecimal total = paid.stream()
                .filter(inv -> {
                    LocalDateTime ts = inv.getPaidAt() != null ? inv.getPaidAt() : inv.getIssuedAt();
                    return ts != null && !ts.isBefore(monthStart) && ts.isBefore(monthEnd);
                })
                .map(FsmInvoice::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            result.add(Map.of("month", label, "amount", total));
        }
        return ResponseEntity.ok(result);
    }

    // ── Jobs count by status ──────────────────────────────────────────────────
    @GetMapping("/jobs-by-status")
    public ResponseEntity<?> jobsByStatus(HttpServletRequest req) {
        Long owner = ownerId(req);
        if (owner == null) return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));

        List<Object[]> rows = jobRepo.countByStatusForOwner(owner);
        List<Map<String, Object>> result = rows.stream()
            .map(r -> Map.<String, Object>of("status", r[0], "count", r[1]))
            .collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    // ── Technician performance (completed jobs) ───────────────────────────────
    @GetMapping("/tech-performance")
    public ResponseEntity<?> techPerformance(HttpServletRequest req) {
        Long owner = ownerId(req);
        if (owner == null) return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));

        List<Object[]> rows = jobRepo.completedJobsPerTech(owner);
        List<Map<String, Object>> result = rows.stream()
            .limit(8)
            .map(r -> Map.<String, Object>of("name", r[0], "count", r[1]))
            .collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    // ── Top job types ─────────────────────────────────────────────────────────
    @GetMapping("/jobs-by-type")
    public ResponseEntity<?> jobsByType(HttpServletRequest req) {
        Long owner = ownerId(req);
        if (owner == null) return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));

        List<Object[]> rows = jobRepo.countByJobTypeForOwner(owner);
        List<Map<String, Object>> result = rows.stream()
            .limit(8)
            .map(r -> Map.<String, Object>of("type", r[0], "count", r[1]))
            .collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }
}
