package com.Shubham.carDealership.fsm.controller;

import com.Shubham.carDealership.config.JwtUtil;
import com.Shubham.carDealership.fsm.dto.CustomerRequest;
import com.Shubham.carDealership.fsm.dto.CustomerResponse;
import com.Shubham.carDealership.fsm.model.FsmCustomer;
import com.Shubham.carDealership.fsm.repository.FsmCustomerRepository;
import com.Shubham.carDealership.model.User;
import com.Shubham.carDealership.repository.UserRepository;
import com.Shubham.carDealership.service.XeroService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/fsm/customers")
public class FsmCustomerController {

    @Autowired private FsmCustomerRepository repo;
    @Autowired private UserRepository        userRepo;
    @Autowired private XeroService           xeroService;
    @Autowired private JwtUtil               jwtUtil;

    private Long ownerId(HttpServletRequest req) {
        String h = req.getHeader("Authorization");
        if (h != null && h.startsWith("Bearer ")) {
            String token = h.substring(7);
            if (jwtUtil.validateToken(token)) return jwtUtil.extractUserId(token);
        }
        return null;
    }

    private CustomerResponse toDto(FsmCustomer c) {
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

    @GetMapping
    public ResponseEntity<?> list(HttpServletRequest req) {
        Long owner = ownerId(req);
        if (owner == null) return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        List<CustomerResponse> list = repo.findByBusinessOwnerIdOrderByNameAsc(owner)
                .stream().map(this::toDto).collect(Collectors.toList());
        return ResponseEntity.ok(list);
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody CustomerRequest body, HttpServletRequest req) {
        Long owner = ownerId(req);
        if (owner == null) return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));

        FsmCustomer c = new FsmCustomer();
        c.setBusinessOwnerId(owner);
        c.setName(body.getName());
        c.setPhone(body.getPhone());
        c.setEmail(body.getEmail());
        c.setAddress(body.getAddress());
        c.setNotes(body.getNotes());
        FsmCustomer saved = repo.save(c);
        try {
            User ownerUser = userRepo.findById(owner).orElse(null);
            if (ownerUser != null) xeroService.syncCustomer(saved, ownerUser);
        } catch (Exception ignored) {}
        return ResponseEntity.ok(toDto(saved));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> get(@PathVariable Long id, HttpServletRequest req) {
        Long owner = ownerId(req);
        if (owner == null) return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        return repo.findById(id)
                .filter(c -> c.getBusinessOwnerId().equals(owner))
                .map(c -> ResponseEntity.ok(toDto(c)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody CustomerRequest body, HttpServletRequest req) {
        Long owner = ownerId(req);
        if (owner == null) return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        return repo.findById(id)
                .filter(c -> c.getBusinessOwnerId().equals(owner))
                .map(c -> {
                    if (body.getName()    != null) c.setName(body.getName());
                    if (body.getPhone()   != null) c.setPhone(body.getPhone());
                    if (body.getEmail()   != null) c.setEmail(body.getEmail());
                    if (body.getAddress() != null) c.setAddress(body.getAddress());
                    if (body.getNotes()   != null) c.setNotes(body.getNotes());
                    return ResponseEntity.ok(toDto(repo.save(c)));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id, HttpServletRequest req) {
        Long owner = ownerId(req);
        if (owner == null) return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        return repo.findById(id)
                .filter(c -> c.getBusinessOwnerId().equals(owner))
                .map(c -> { repo.delete(c); return ResponseEntity.ok(Map.of("success", true)); })
                .orElse(ResponseEntity.notFound().build());
    }
}
