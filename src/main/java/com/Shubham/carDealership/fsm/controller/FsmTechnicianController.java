package com.Shubham.carDealership.fsm.controller;

import com.Shubham.carDealership.config.JwtUtil;
import com.Shubham.carDealership.fsm.dto.TechnicianRequest;
import com.Shubham.carDealership.fsm.dto.TechnicianResponse;
import com.Shubham.carDealership.fsm.model.FsmTechnician;
import com.Shubham.carDealership.fsm.repository.FsmTechnicianRepository;
import com.Shubham.carDealership.model.User;
import com.Shubham.carDealership.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/fsm/technicians")
public class FsmTechnicianController {

    @Autowired private FsmTechnicianRepository repo;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtUtil jwtUtil;

    private Long ownerId(HttpServletRequest req) {
        String h = req.getHeader("Authorization");
        if (h != null && h.startsWith("Bearer ")) {
            String token = h.substring(7);
            if (jwtUtil.validateToken(token)) return jwtUtil.extractUserId(token);
        }
        return null;
    }

    private TechnicianResponse toDto(FsmTechnician t) {
        TechnicianResponse r = new TechnicianResponse();
        r.setId(t.getId());
        r.setName(t.getName());
        r.setPhone(t.getPhone());
        r.setEmail(t.getEmail());
        r.setStatus(t.getStatus());
        r.setSkills(t.getSkills());
        r.setUserId(t.getUserId());
        r.setHasLogin(t.getUserId() != null);
        r.setCreatedAt(t.getCreatedAt());
        return r;
    }

    @GetMapping
    public ResponseEntity<?> list(HttpServletRequest req) {
        Long owner = ownerId(req);
        if (owner == null) return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        List<TechnicianResponse> list = repo.findByBusinessOwnerIdOrderByNameAsc(owner)
                .stream().map(this::toDto).collect(Collectors.toList());
        return ResponseEntity.ok(list);
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody TechnicianRequest body, HttpServletRequest req) {
        Long owner = ownerId(req);
        if (owner == null) return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));

        // Starter plan: max 3 technicians
        var ownerUser = userRepository.findById(owner).orElse(null);
        if (ownerUser != null && "STARTER".equals(ownerUser.getPlan())) {
            long techCount = repo.countByBusinessOwnerId(owner);
            if (techCount >= 3) {
                return ResponseEntity.status(402).body(Map.of(
                    "error", "You've reached the 3 technician limit on the Starter plan. Upgrade to Pro to add more.",
                    "upgrade", true,
                    "limit", "technicians"
                ));
            }
        }

        FsmTechnician t = new FsmTechnician();
        t.setBusinessOwnerId(owner);
        t.setName(body.getName());
        t.setPhone(body.getPhone());
        t.setEmail(body.getEmail());
        t.setStatus(body.getStatus() != null ? body.getStatus() : "AVAILABLE");
        t.setSkills(body.getSkills());

        String tempPassword = null;

        if (body.isCreateLogin() && body.getEmail() != null && !body.getEmail().isBlank()) {
            if (userRepository.existsByEmail(body.getEmail())) {
                return ResponseEntity.badRequest().body(Map.of("error",
                        "A user account with this email already exists"));
            }
            tempPassword = body.getPassword() != null && !body.getPassword().isBlank()
                    ? body.getPassword()
                    : UUID.randomUUID().toString().substring(0, 10);

            User techUser = new User();
            techUser.setUsername(body.getName().trim().toLowerCase().replace("\\s+", "_")
                    + "_" + UUID.randomUUID().toString().substring(0, 4));
            techUser.setEmail(body.getEmail());
            techUser.setPassword(passwordEncoder.encode(tempPassword));
            techUser.setRole("TECHNICIAN");
            techUser.setIsEmployee(true);
            User saved = userRepository.save(techUser);
            t.setUserId(saved.getId());
        }

        FsmTechnician saved = repo.save(t);
        TechnicianResponse resp = toDto(saved);
        resp.setTempPassword(tempPassword);
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> get(@PathVariable Long id, HttpServletRequest req) {
        Long owner = ownerId(req);
        if (owner == null) return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        return repo.findById(id)
                .filter(t -> t.getBusinessOwnerId().equals(owner))
                .map(t -> ResponseEntity.ok(toDto(t)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody TechnicianRequest body, HttpServletRequest req) {
        Long owner = ownerId(req);
        if (owner == null) return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        return repo.findById(id)
                .filter(t -> t.getBusinessOwnerId().equals(owner))
                .map(t -> {
                    if (body.getName()   != null) t.setName(body.getName());
                    if (body.getPhone()  != null) t.setPhone(body.getPhone());
                    if (body.getEmail()  != null) t.setEmail(body.getEmail());
                    if (body.getStatus() != null) t.setStatus(body.getStatus());
                    if (body.getSkills() != null) t.setSkills(body.getSkills());
                    return ResponseEntity.ok(toDto(repo.save(t)));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<?> updateStatus(@PathVariable Long id, @RequestBody Map<String, String> body, HttpServletRequest req) {
        Long owner = ownerId(req);
        if (owner == null) return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        return repo.findById(id)
                .filter(t -> t.getBusinessOwnerId().equals(owner))
                .map(t -> {
                    t.setStatus(body.get("status"));
                    return ResponseEntity.ok(toDto(repo.save(t)));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id, HttpServletRequest req) {
        Long owner = ownerId(req);
        if (owner == null) return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        return repo.findById(id)
                .filter(t -> t.getBusinessOwnerId().equals(owner))
                .map(t -> { repo.delete(t); return ResponseEntity.ok(Map.of("success", true)); })
                .orElse(ResponseEntity.notFound().build());
    }
}
