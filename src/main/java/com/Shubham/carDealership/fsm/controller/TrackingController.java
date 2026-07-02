package com.Shubham.carDealership.fsm.controller;

import com.Shubham.carDealership.fsm.repository.FsmJobRepository;
import com.Shubham.carDealership.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/track")
public class TrackingController {

    @Autowired private FsmJobRepository jobRepo;
    @Autowired private UserRepository userRepo;

    private static final List<String> STEPS =
        List.of("NEW", "SCHEDULED", "DISPATCHED", "IN_PROGRESS", "COMPLETED", "INVOICED");

    @GetMapping("/{key}")
    public ResponseEntity<?> track(@PathVariable String key) {
        return jobRepo.findByTrackingKey(key)
                .map(job -> {
                    Map<String, Object> res = new HashMap<>();
                    res.put("jobType",     job.getJobType());
                    res.put("status",      job.getStatus());
                    res.put("priority",    job.getPriority());
                    res.put("address",     job.getAddress());
                    res.put("description", job.getDescription());
                    res.put("notes",       job.getNotes());
                    res.put("scheduledAt", job.getScheduledAt());
                    res.put("completedAt", job.getCompletedAt());
                    res.put("createdAt",   job.getCreatedAt());
                    res.put("steps",       STEPS);
                    res.put("currentStep", STEPS.indexOf(job.getStatus()));

                    // First name only for privacy
                    if (job.getTechnician() != null) {
                        String fullName = job.getTechnician().getName();
                        res.put("techName",   fullName != null ? fullName.split(" ")[0] : null);
                        res.put("techSkills", job.getTechnician().getSkills());
                    } else {
                        res.put("techName",   null);
                        res.put("techSkills", null);
                    }

                    // Business name from manager's account
                    userRepo.findById(job.getBusinessOwnerId()).ifPresent(u ->
                            res.put("businessName", u.getBusinessName())
                    );

                    return ResponseEntity.ok(res);
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
