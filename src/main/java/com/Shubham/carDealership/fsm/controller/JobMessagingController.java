package com.Shubham.carDealership.fsm.controller;

import com.Shubham.carDealership.config.JwtUtil;
import com.Shubham.carDealership.fsm.repository.FsmJobRepository;
import com.Shubham.carDealership.fsm.repository.JobMessageRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST endpoints only. WebSocket @MessageMapping lives in JobWebSocketController.
 * Having @RestController on the same class as @MessageMapping breaks WebSocket handler registration.
 */
@RestController
@RequestMapping("/api/job-messages")
public class JobMessagingController {

    @Autowired private JobMessageRepository msgRepo;
    @Autowired private FsmJobRepository jobRepo;
    @Autowired private JwtUtil jwtUtil;

    // ── REST: get message history by tracking key (public — customer uses on load) ──
    @GetMapping("/{trackingKey}")
    public ResponseEntity<?> history(@PathVariable String trackingKey) {
        List<Map<String, Object>> msgs = msgRepo
                .findByTrackingKeyOrderBySentAtAsc(trackingKey)
                .stream()
                .map(JobMessagingController::msgToMap)
                .collect(Collectors.toList());
        return ResponseEntity.ok(msgs);
    }

    // ── REST: get message history by job ID (authenticated — manager/tech) ─
    @GetMapping("/job/{jobId}")
    public ResponseEntity<?> historyByJob(@PathVariable Long jobId, HttpServletRequest req) {
        String h = req.getHeader("Authorization");
        if (h == null || !h.startsWith("Bearer ") || !jwtUtil.validateToken(h.substring(7)))
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));

        return jobRepo.findById(jobId).map(job -> {
            List<Map<String, Object>> msgs = msgRepo
                    .findByTrackingKeyOrderBySentAtAsc(job.getTrackingKey())
                    .stream().map(JobMessagingController::msgToMap).collect(Collectors.toList());
            return ResponseEntity.ok(msgs);
        }).orElse(ResponseEntity.notFound().build());
    }

    // ── Unread count for manager ─────────────────────────────────────────────
    // Returns count of CUSTOMER or TECHNICIAN messages on this business's jobs
    // sent after ?since=<epoch-millis>
    @GetMapping("/unread-count")
    public ResponseEntity<?> unreadCount(@RequestParam(required = false) Long since, HttpServletRequest req) {
        String h = req.getHeader("Authorization");
        if (h == null || !h.startsWith("Bearer ") || !jwtUtil.validateToken(h.substring(7)))
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        Long ownerId = jwtUtil.extractUserId(h.substring(7));

        LocalDateTime sinceTime = since != null
                ? LocalDateTime.ofInstant(Instant.ofEpochMilli(since), ZoneId.systemDefault())
                : LocalDateTime.now().minusDays(7);

        List<Long> jobIds = jobRepo.findByBusinessOwnerIdOrderByScheduledAtDesc(ownerId)
                .stream().map(j -> j.getId()).collect(Collectors.toList());

        long count = jobIds.isEmpty() ? 0L
                : msgRepo.countNewMessages(jobIds, List.of("CUSTOMER", "TECHNICIAN"), sinceTime);

        return ResponseEntity.ok(Map.of("count", count));
    }

    static Map<String, Object> msgToMap(com.Shubham.carDealership.fsm.model.JobMessage m) {
        return Map.of(
            "id",         m.getId(),
            "senderType", m.getSenderType(),
            "senderName", m.getSenderName() != null ? m.getSenderName() : m.getSenderType(),
            "message",    m.getMessage(),
            "sentAt",     m.getSentAt().toString()
        );
    }
}
