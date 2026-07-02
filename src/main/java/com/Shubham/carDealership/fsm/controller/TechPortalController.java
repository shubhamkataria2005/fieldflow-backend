package com.Shubham.carDealership.fsm.controller;

import com.Shubham.carDealership.config.JwtUtil;
import com.Shubham.carDealership.fsm.dto.CustomerResponse;
import com.Shubham.carDealership.fsm.dto.JobResponse;
import com.Shubham.carDealership.fsm.dto.TechnicianResponse;
import com.Shubham.carDealership.fsm.model.FsmCustomer;
import com.Shubham.carDealership.fsm.model.FsmJob;
import com.Shubham.carDealership.fsm.model.FsmTechnician;
import com.Shubham.carDealership.fsm.repository.FsmJobRepository;
import com.Shubham.carDealership.fsm.repository.FsmTechnicianRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.Shubham.carDealership.fsm.repository.JobMessageRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/tech")
public class TechPortalController {

    @Autowired private FsmTechnicianRepository techRepo;
    @Autowired private FsmJobRepository jobRepo;
    @Autowired private JobMessageRepository msgRepo;
    @Autowired private JwtUtil jwtUtil;

    private Long userId(HttpServletRequest req) {
        String h = req.getHeader("Authorization");
        if (h != null && h.startsWith("Bearer ")) {
            String token = h.substring(7);
            if (jwtUtil.validateToken(token)) return jwtUtil.extractUserId(token);
        }
        return null;
    }

    private FsmTechnician resolveTech(HttpServletRequest req) {
        Long uid = userId(req);
        if (uid == null) return null;
        return techRepo.findByUserId(uid).orElse(null);
    }

    private TechnicianResponse techDto(FsmTechnician t) {
        TechnicianResponse r = new TechnicianResponse();
        r.setId(t.getId());
        r.setName(t.getName());
        r.setPhone(t.getPhone());
        r.setEmail(t.getEmail());
        r.setStatus(t.getStatus());
        r.setSkills(t.getSkills());
        r.setUserId(t.getUserId());
        r.setHasLogin(true);
        r.setCreatedAt(t.getCreatedAt());
        return r;
    }

    private CustomerResponse customerDto(FsmCustomer c) {
        if (c == null) return null;
        CustomerResponse r = new CustomerResponse();
        r.setId(c.getId());
        r.setName(c.getName());
        r.setPhone(c.getPhone());
        r.setEmail(c.getEmail());
        r.setAddress(c.getAddress());
        r.setNotes(c.getNotes());
        r.setCreatedAt(c.getCreatedAt());
        return r;
    }

    private JobResponse jobDto(FsmJob j) {
        // Backfill tracking key for jobs created before the field was added
        if (j.getTrackingKey() == null) {
            j.setTrackingKey(java.util.UUID.randomUUID().toString());
            jobRepo.save(j);
        }
        JobResponse r = new JobResponse();
        r.setId(j.getId());
        r.setCustomer(customerDto(j.getCustomer()));
        r.setTechnician(j.getTechnician() != null ? techDto(j.getTechnician()) : null);
        r.setJobType(j.getJobType());
        r.setStatus(j.getStatus());
        r.setPriority(j.getPriority());
        r.setAddress(j.getAddress());
        r.setDescription(j.getDescription());
        r.setNotes(j.getNotes());
        r.setPartsUsed(j.getPartsUsed());
        r.setAmount(j.getAmount());
        r.setScheduledAt(j.getScheduledAt());
        r.setCreatedAt(j.getCreatedAt());
        r.setCompletedAt(j.getCompletedAt());
        r.setTrackingKey(j.getTrackingKey());
        return r;
    }

    @GetMapping("/profile")
    public ResponseEntity<?> profile(HttpServletRequest req) {
        FsmTechnician tech = resolveTech(req);
        if (tech == null) return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        return ResponseEntity.ok(techDto(tech));
    }

    @GetMapping("/jobs")
    public ResponseEntity<?> myJobs(HttpServletRequest req) {
        FsmTechnician tech = resolveTech(req);
        if (tech == null) return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        List<JobResponse> jobs = jobRepo.findByTechnician_IdOrderByScheduledAtDesc(tech.getId())
                .stream().map(this::jobDto).collect(Collectors.toList());
        return ResponseEntity.ok(jobs);
    }

    @GetMapping("/jobs/today")
    public ResponseEntity<?> todayJobs(HttpServletRequest req) {
        FsmTechnician tech = resolveTech(req);
        if (tech == null) return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        LocalDateTime start = LocalDate.now().atStartOfDay();
        LocalDateTime end   = start.plusDays(1);
        List<JobResponse> jobs = jobRepo
                .findByTechnician_IdAndScheduledAtBetweenOrderByScheduledAtAsc(tech.getId(), start, end)
                .stream().map(this::jobDto).collect(Collectors.toList());
        return ResponseEntity.ok(jobs);
    }

    @PatchMapping("/jobs/{id}/status")
    public ResponseEntity<?> updateStatus(@PathVariable Long id,
                                           @RequestBody Map<String, String> body,
                                           HttpServletRequest req) {
        FsmTechnician tech = resolveTech(req);
        if (tech == null) return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        return jobRepo.findById(id)
                .filter(j -> j.getTechnician() != null && j.getTechnician().getId().equals(tech.getId()))
                .map(j -> {
                    String newStatus = body.get("status");
                    j.setStatus(newStatus);
                    if ("COMPLETED".equals(newStatus)) j.setCompletedAt(LocalDateTime.now());
                    return ResponseEntity.ok(jobDto(jobRepo.save(j)));
                })
                .orElse(ResponseEntity.status(403).build());
    }

    @PatchMapping("/jobs/{id}/notes")
    public ResponseEntity<?> updateNotes(@PathVariable Long id,
                                          @RequestBody Map<String, String> body,
                                          HttpServletRequest req) {
        FsmTechnician tech = resolveTech(req);
        if (tech == null) return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        return jobRepo.findById(id)
                .filter(j -> j.getTechnician() != null && j.getTechnician().getId().equals(tech.getId()))
                .map(j -> {
                    if (body.containsKey("notes"))     j.setNotes(body.get("notes"));
                    if (body.containsKey("partsUsed")) j.setPartsUsed(body.get("partsUsed"));
                    return ResponseEntity.ok(jobDto(jobRepo.save(j)));
                })
                .orElse(ResponseEntity.status(403).build());
    }

    @PatchMapping("/status")
    public ResponseEntity<?> updateMyStatus(@RequestBody Map<String, String> body,
                                             HttpServletRequest req) {
        FsmTechnician tech = resolveTech(req);
        if (tech == null) return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        tech.setStatus(body.get("status"));
        return ResponseEntity.ok(techDto(techRepo.save(tech)));
    }

    @GetMapping("/messages/unread-count")
    public ResponseEntity<?> unreadCount(@RequestParam(required = false) Long since, HttpServletRequest req) {
        FsmTechnician tech = resolveTech(req);
        if (tech == null) return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));

        LocalDateTime sinceTime = since != null
                ? LocalDateTime.ofInstant(Instant.ofEpochMilli(since), ZoneId.systemDefault())
                : LocalDateTime.now().minusDays(7);

        List<Long> jobIds = jobRepo.findByTechnician_IdOrderByScheduledAtDesc(tech.getId())
                .stream().map(j -> j.getId()).collect(Collectors.toList());

        long count = jobIds.isEmpty() ? 0L
                : msgRepo.countNewMessages(jobIds, List.of("CUSTOMER", "MANAGER"), sinceTime);

        return ResponseEntity.ok(Map.of("count", count));
    }
}
