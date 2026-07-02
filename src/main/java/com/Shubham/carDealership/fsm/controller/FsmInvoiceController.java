package com.Shubham.carDealership.fsm.controller;

import com.Shubham.carDealership.config.JwtUtil;
import com.Shubham.carDealership.fsm.dto.CustomerResponse;
import com.Shubham.carDealership.fsm.dto.InvoiceResponse;
import com.Shubham.carDealership.fsm.model.FsmCustomer;
import com.Shubham.carDealership.fsm.model.FsmInvoice;
import com.Shubham.carDealership.fsm.model.FsmJob;
import com.Shubham.carDealership.fsm.repository.FsmInvoiceRepository;
import com.Shubham.carDealership.fsm.repository.FsmJobRepository;
import com.Shubham.carDealership.fsm.service.InvoicePdfService;
import com.Shubham.carDealership.model.User;
import com.Shubham.carDealership.repository.UserRepository;
import com.Shubham.carDealership.service.EmailService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/fsm/invoices")
public class FsmInvoiceController {

    @Autowired private FsmInvoiceRepository repo;
    @Autowired private FsmJobRepository     jobRepo;
    @Autowired private UserRepository       userRepo;
    @Autowired private JwtUtil              jwtUtil;
    @Autowired private InvoicePdfService    pdfService;
    @Autowired private EmailService         emailService;

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
        return r;
    }

    private InvoiceResponse toDto(FsmInvoice inv) {
        InvoiceResponse r = new InvoiceResponse();
        r.setId(inv.getId());
        r.setJobId(inv.getJob() != null ? inv.getJob().getId() : null);
        r.setJobType(inv.getJob() != null ? inv.getJob().getJobType() : null);
        r.setCustomer(customerDto(inv.getCustomer()));
        r.setStatus(inv.getStatus());
        r.setAmount(inv.getAmount());
        r.setPaymentMethod(inv.getPaymentMethod());
        r.setIssuedAt(inv.getIssuedAt());
        r.setPaidAt(inv.getPaidAt());
        return r;
    }

    @GetMapping
    public ResponseEntity<?> list(@RequestParam(required = false) String status, HttpServletRequest req) {
        Long owner = ownerId(req);
        if (owner == null) return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        List<FsmInvoice> invoices = (status != null && !status.isBlank())
                ? repo.findByBusinessOwnerIdAndStatusOrderByIssuedAtDesc(owner, status.toUpperCase())
                : repo.findByBusinessOwnerIdOrderByIssuedAtDesc(owner);
        return ResponseEntity.ok(invoices.stream().map(this::toDto).collect(Collectors.toList()));
    }

    @GetMapping("/job/{jobId}")
    public ResponseEntity<?> byJob(@PathVariable Long jobId, HttpServletRequest req) {
        Long owner = ownerId(req);
        if (owner == null) return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        return repo.findByJobId(jobId)
                .filter(inv -> inv.getBusinessOwnerId().equals(owner))
                .map(inv -> ResponseEntity.ok(toDto(inv)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody Map<String, Object> body, HttpServletRequest req) {
        Long owner = ownerId(req);
        if (owner == null) return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));

        // Idempotent: if an invoice already exists for this job, return it
        if (body.get("jobId") != null) {
            Long jobId = Long.valueOf(body.get("jobId").toString());
            var existing = repo.findByJobId(jobId)
                    .filter(inv -> inv.getBusinessOwnerId().equals(owner));
            if (existing.isPresent()) {
                return ResponseEntity.ok(toDto(existing.get()));
            }
        }

        FsmInvoice inv = new FsmInvoice();
        inv.setBusinessOwnerId(owner);

        if (body.get("jobId") != null) {
            Long jobId = Long.valueOf(body.get("jobId").toString());
            jobRepo.findById(jobId)
                    .filter(j -> j.getBusinessOwnerId().equals(owner))
                    .ifPresent(j -> {
                        inv.setJob(j);
                        inv.setCustomer(j.getCustomer());
                        // Use job amount as default; frontend may send an override
                        inv.setAmount(j.getAmount());
                    });
        }
        if (body.get("amount") != null) {
            inv.setAmount(new java.math.BigDecimal(body.get("amount").toString()));
        }
        if (body.get("status") != null) inv.setStatus(body.get("status").toString());

        return ResponseEntity.ok(toDto(repo.save(inv)));
    }

    @PutMapping("/{id}/amount")
    public ResponseEntity<?> updateAmount(@PathVariable Long id, @RequestBody Map<String, Object> body, HttpServletRequest req) {
        Long owner = ownerId(req);
        if (owner == null) return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        return repo.findById(id)
                .filter(inv -> inv.getBusinessOwnerId().equals(owner))
                .map(inv -> {
                    if (body.get("amount") != null)
                        inv.setAmount(new java.math.BigDecimal(body.get("amount").toString()));
                    return ResponseEntity.ok(toDto(repo.save(inv)));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PatchMapping("/{id}/pay")
    public ResponseEntity<?> markPaid(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body, HttpServletRequest req) {
        Long owner = ownerId(req);
        if (owner == null) return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        return repo.findById(id)
                .filter(inv -> inv.getBusinessOwnerId().equals(owner))
                .map(inv -> {
                    inv.setStatus("PAID");
                    inv.setPaidAt(LocalDateTime.now());
                    if (body != null && body.get("paymentMethod") != null) {
                        inv.setPaymentMethod(body.get("paymentMethod"));
                    }
                    // Mark the linked job as INVOICED
                    if (inv.getJob() != null) {
                        FsmJob job = inv.getJob();
                        job.setStatus("INVOICED");
                        jobRepo.save(job);
                    }
                    return ResponseEntity.ok(toDto(repo.save(inv)));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PatchMapping("/{id}/overdue")
    public ResponseEntity<?> markOverdue(@PathVariable Long id, HttpServletRequest req) {
        Long owner = ownerId(req);
        if (owner == null) return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        return repo.findById(id)
                .filter(inv -> inv.getBusinessOwnerId().equals(owner))
                .map(inv -> {
                    inv.setStatus("OVERDUE");
                    return ResponseEntity.ok(toDto(repo.save(inv)));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // ── Download PDF ─────────────────────────────────────────────────────────
    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> downloadPdf(@PathVariable Long id, HttpServletRequest req) {
        Long owner = ownerId(req);
        if (owner == null) return ResponseEntity.status(401).build();
        return repo.findById(id)
                .filter(inv -> inv.getBusinessOwnerId().equals(owner))
                .map(inv -> {
                    String bizName = userRepo.findById(owner).map(User::getUsername).orElse("FieldFlow");
                    byte[] pdf = pdfService.generate(inv, bizName);
                    String filename = "invoice-" + String.format("%04d", inv.getId()) + ".pdf";
                    return ResponseEntity.ok()
                            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                            .contentType(MediaType.APPLICATION_PDF)
                            .body(pdf);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // ── Email invoice to customer ─────────────────────────────────────────────
    @PostMapping("/{id}/send")
    public ResponseEntity<?> sendInvoice(@PathVariable Long id, HttpServletRequest req) {
        Long owner = ownerId(req);
        if (owner == null) return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        return repo.findById(id)
                .filter(inv -> inv.getBusinessOwnerId().equals(owner))
                .map(inv -> {
                    if (inv.getCustomer() == null || inv.getCustomer().getEmail() == null
                            || inv.getCustomer().getEmail().isBlank()) {
                        return ResponseEntity.badRequest().body(Map.of("error", "Customer has no email address"));
                    }
                    String bizName = userRepo.findById(owner).map(User::getUsername).orElse("FieldFlow");
                    emailService.sendInvoiceToCustomer(inv, bizName);
                    return ResponseEntity.ok(Map.of("message", "Invoice sent"));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // ── Send payment reminder ─────────────────────────────────────────────────
    @PostMapping("/{id}/remind")
    public ResponseEntity<?> sendReminder(@PathVariable Long id, HttpServletRequest req) {
        Long owner = ownerId(req);
        if (owner == null) return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        return repo.findById(id)
                .filter(inv -> inv.getBusinessOwnerId().equals(owner))
                .map(inv -> {
                    if (inv.getCustomer() == null || inv.getCustomer().getEmail() == null
                            || inv.getCustomer().getEmail().isBlank()) {
                        return ResponseEntity.badRequest().body(Map.of("error", "Customer has no email address"));
                    }
                    String bizName = userRepo.findById(owner).map(User::getUsername).orElse("FieldFlow");
                    emailService.sendPaymentReminder(inv, bizName);
                    return ResponseEntity.ok(Map.of("message", "Reminder sent"));
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
