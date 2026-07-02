package com.Shubham.carDealership.fsm.controller;

import com.Shubham.carDealership.config.JwtUtil;
import com.Shubham.carDealership.fsm.dto.CustomerResponse;
import com.Shubham.carDealership.fsm.dto.JobRequest;
import com.Shubham.carDealership.fsm.dto.JobResponse;
import com.Shubham.carDealership.fsm.dto.TechnicianResponse;
import com.Shubham.carDealership.fsm.model.FsmCustomer;
import com.Shubham.carDealership.fsm.model.FsmJob;
import com.Shubham.carDealership.fsm.model.FsmTechnician;
import com.Shubham.carDealership.fsm.repository.FsmCustomerRepository;
import com.Shubham.carDealership.fsm.repository.FsmJobRepository;
import com.Shubham.carDealership.fsm.repository.FsmTechnicianRepository;
import com.Shubham.carDealership.repository.UserRepository;
import com.Shubham.carDealership.service.EmailService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/fsm/jobs")
public class FsmJobController {

    @Autowired private FsmJobRepository repo;
    @Autowired private FsmCustomerRepository customerRepo;
    @Autowired private FsmTechnicianRepository techRepo;
    @Autowired private UserRepository userRepo;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private EmailService emailService;

    @Value("${app.frontend.url:https://dealership.shubhamkataria.com}")
    private String frontendUrl;

    private void fireStatusEmail(FsmJob j, String newStatus, Long ownerId) {
        FsmCustomer cust = j.getCustomer();
        if (cust == null || cust.getEmail() == null || cust.getEmail().isBlank()) return;
        if (j.getTrackingKey() == null) return;
        // Only email on meaningful forward transitions
        if (!java.util.Set.of("SCHEDULED","DISPATCHED","IN_PROGRESS","COMPLETED","INVOICED").contains(newStatus)) return;
        String bizName   = userRepo.findById(ownerId)
            .map(u -> u.getBusinessName() != null ? u.getBusinessName() : "FieldFlow")
            .orElse("FieldFlow");
        String trackUrl  = frontendUrl + "/track/" + j.getTrackingKey();
        String custEmail = cust.getEmail();
        String custName  = cust.getName();
        String jobType   = j.getJobType();
        CompletableFuture.runAsync(() ->
            emailService.sendJobStatusUpdate(custEmail, custName, jobType, newStatus, trackUrl, bizName)
        );
    }

    private Long ownerId(HttpServletRequest req) {
        String h = req.getHeader("Authorization");
        if (h != null && h.startsWith("Bearer ")) {
            String token = h.substring(7);
            if (jwtUtil.validateToken(token)) return jwtUtil.extractUserId(token);
        }
        return null;
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

    private TechnicianResponse techDto(FsmTechnician t) {
        if (t == null) return null;
        TechnicianResponse r = new TechnicianResponse();
        r.setId(t.getId());
        r.setName(t.getName());
        r.setPhone(t.getPhone());
        r.setEmail(t.getEmail());
        r.setStatus(t.getStatus());
        r.setSkills(t.getSkills());
        r.setCreatedAt(t.getCreatedAt());
        return r;
    }

    private JobResponse toDto(FsmJob j) {
        // Backfill tracking key for jobs created before the field was added
        if (j.getTrackingKey() == null) {
            j.setTrackingKey(java.util.UUID.randomUUID().toString());
            repo.save(j);
        }

        JobResponse r = new JobResponse();
        r.setId(j.getId());
        r.setCustomer(customerDto(j.getCustomer()));
        r.setTechnician(techDto(j.getTechnician()));
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

    @GetMapping
    public ResponseEntity<?> list(@RequestParam(required = false) String status, HttpServletRequest req) {
        Long owner = ownerId(req);
        if (owner == null) return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        List<FsmJob> jobs = (status != null && !status.isBlank())
                ? repo.findByBusinessOwnerIdAndStatusOrderByScheduledAtAsc(owner, status.toUpperCase())
                : repo.findByBusinessOwnerIdOrderByScheduledAtDesc(owner);
        return ResponseEntity.ok(jobs.stream().map(this::toDto).collect(Collectors.toList()));
    }

    @GetMapping("/today")
    public ResponseEntity<?> today(HttpServletRequest req) {
        Long owner = ownerId(req);
        if (owner == null) return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        LocalDateTime start = LocalDateTime.now().toLocalDate().atStartOfDay();
        LocalDateTime end = start.plusDays(1);
        List<JobResponse> jobs = repo.findByBusinessOwnerIdAndScheduledAtBetweenOrderByScheduledAtAsc(owner, start, end)
                .stream().map(this::toDto).collect(Collectors.toList());
        return ResponseEntity.ok(jobs);
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody JobRequest body, HttpServletRequest req) {
        Long owner = ownerId(req);
        if (owner == null) return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));

        FsmJob j = new FsmJob();
        j.setBusinessOwnerId(owner);
        j.setJobType(body.getJobType());
        j.setStatus(body.getStatus() != null ? body.getStatus() : "NEW");
        j.setPriority(body.getPriority() != null ? body.getPriority() : "NORMAL");
        j.setAddress(body.getAddress());
        j.setDescription(body.getDescription());
        j.setNotes(body.getNotes());
        j.setPartsUsed(body.getPartsUsed());
        if (body.getAmount() != null) j.setAmount(body.getAmount());
        j.setScheduledAt(body.getScheduledAt());

        if (body.getCustomerId() != null) {
            customerRepo.findById(body.getCustomerId())
                    .filter(c -> c.getBusinessOwnerId().equals(owner))
                    .ifPresent(j::setCustomer);
        }
        if (body.getTechnicianId() != null) {
            techRepo.findById(body.getTechnicianId())
                    .filter(t -> t.getBusinessOwnerId().equals(owner))
                    .ifPresent(tech -> {
                        j.setTechnician(tech);
                        if (tech.getEmail() != null && !tech.getEmail().isBlank()) {
                            String jt = j.getJobType(), addr = j.getAddress();
                            LocalDateTime at = j.getScheduledAt();
                            CompletableFuture.runAsync(() ->
                                emailService.sendTechJobAssigned(tech.getEmail(), tech.getName(), jt, addr, at));
                        }
                    });
        }

        return ResponseEntity.ok(toDto(repo.save(j)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> get(@PathVariable Long id, HttpServletRequest req) {
        Long owner = ownerId(req);
        if (owner == null) return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        return repo.findById(id)
                .filter(j -> j.getBusinessOwnerId().equals(owner))
                .map(j -> ResponseEntity.ok(toDto(j)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody JobRequest body, HttpServletRequest req) {
        Long owner = ownerId(req);
        if (owner == null) return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        return repo.findById(id)
                .filter(j -> j.getBusinessOwnerId().equals(owner))
                .map(j -> {
                    if (body.getJobType()     != null) j.setJobType(body.getJobType());
                    if (body.getPriority()    != null) j.setPriority(body.getPriority());
                    if (body.getAddress()     != null) j.setAddress(body.getAddress());
                    if (body.getDescription() != null) j.setDescription(body.getDescription());
                    if (body.getNotes()       != null) j.setNotes(body.getNotes());
                    if (body.getPartsUsed()   != null) j.setPartsUsed(body.getPartsUsed());
                    if (body.getAmount()      != null) j.setAmount(body.getAmount());
                    if (body.getScheduledAt() != null) j.setScheduledAt(body.getScheduledAt());
                    String prevStatus = j.getStatus();
                    if (body.getStatus() != null) {
                        j.setStatus(body.getStatus());
                        if ("COMPLETED".equals(body.getStatus()) && j.getCompletedAt() == null) {
                            j.setCompletedAt(LocalDateTime.now());
                        }
                    }
                    if (body.getCustomerId() != null) {
                        customerRepo.findById(body.getCustomerId())
                                .filter(c -> c.getBusinessOwnerId().equals(owner))
                                .ifPresent(j::setCustomer);
                    }
                    if (body.getTechnicianId() != null) {
                        techRepo.findById(body.getTechnicianId())
                                .filter(t -> t.getBusinessOwnerId().equals(owner))
                                .ifPresent(j::setTechnician);
                    }
                    FsmJob saved = repo.save(j);
                    if (body.getStatus() != null && !body.getStatus().equals(prevStatus)) {
                        fireStatusEmail(saved, body.getStatus(), owner);
                    }
                    return ResponseEntity.ok(toDto(saved));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<?> updateStatus(@PathVariable Long id, @RequestBody Map<String, String> body, HttpServletRequest req) {
        Long owner = ownerId(req);
        if (owner == null) return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        return repo.findById(id)
                .filter(j -> j.getBusinessOwnerId().equals(owner))
                .map(j -> {
                    String newStatus = body.get("status");
                    j.setStatus(newStatus);
                    if ("COMPLETED".equals(newStatus) && j.getCompletedAt() == null) {
                        j.setCompletedAt(LocalDateTime.now());
                    }
                    FsmJob saved = repo.save(j);
                    fireStatusEmail(saved, newStatus, owner);
                    return ResponseEntity.ok(toDto(saved));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PatchMapping("/{id}/assign")
    public ResponseEntity<?> assign(@PathVariable Long id, @RequestBody Map<String, Long> body, HttpServletRequest req) {
        Long owner = ownerId(req);
        if (owner == null) return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        return repo.findById(id)
                .filter(j -> j.getBusinessOwnerId().equals(owner))
                .map(j -> {
                    Long techId = body.get("technicianId");
                    if (techId != null) {
                        techRepo.findById(techId)
                                .filter(t -> t.getBusinessOwnerId().equals(owner))
                                .ifPresent(tech -> {
                                    j.setTechnician(tech);
                                    // Email the technician (fire-and-forget)
                                    if (tech.getEmail() != null && !tech.getEmail().isBlank()) {
                                        String jobType  = j.getJobType();
                                        String address  = j.getAddress();
                                        LocalDateTime at = j.getScheduledAt();
                                        CompletableFuture.runAsync(() ->
                                            emailService.sendTechJobAssigned(
                                                tech.getEmail(), tech.getName(),
                                                jobType, address, at)
                                        );
                                    }
                                });
                    }
                    return ResponseEntity.ok(toDto(repo.save(j)));
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
