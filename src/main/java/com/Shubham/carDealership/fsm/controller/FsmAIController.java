package com.Shubham.carDealership.fsm.controller;

import com.Shubham.carDealership.config.JwtUtil;
import com.Shubham.carDealership.fsm.model.FsmJob;
import com.Shubham.carDealership.fsm.model.FsmTechnician;
import com.Shubham.carDealership.fsm.repository.FsmCustomerRepository;
import com.Shubham.carDealership.fsm.repository.FsmJobRepository;
import com.Shubham.carDealership.fsm.repository.FsmTechnicianRepository;
import com.Shubham.carDealership.repository.UserRepository;
import com.Shubham.carDealership.service.FsmRAGService;
import com.Shubham.carDealership.service.OpenAIService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/fsm/ai")
public class FsmAIController {

    @Autowired private FsmJobRepository jobRepo;
    @Autowired private FsmTechnicianRepository techRepo;
    @Autowired private FsmCustomerRepository customerRepo;
    @Autowired private UserRepository userRepo;
    @Autowired private OpenAIService openAI;
    @Autowired private FsmRAGService fsmRAG;
    @Autowired private JwtUtil jwtUtil;

    private Long ownerId(HttpServletRequest req) {
        String h = req.getHeader("Authorization");
        if (h != null && h.startsWith("Bearer ")) {
            String token = h.substring(7);
            if (jwtUtil.validateToken(token)) return jwtUtil.extractUserId(token);
        }
        return null;
    }

    // ── Backfill: index all existing records for this business into Pinecone ──
    @PostMapping("/backfill")
    public ResponseEntity<?> backfill(HttpServletRequest req) {
        Long owner = ownerId(req);
        if (owner == null) return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));

        if (!fsmRAG.isAvailable())
            return ResponseEntity.badRequest().body(Map.of("error", "RAG not configured — set PINECONE_API_KEY and PINECONE_INDEX_URL"));

        List<FsmJob>        jobs      = jobRepo.findByBusinessOwnerIdOrderByScheduledAtDesc(owner);
        List<FsmTechnician> techs     = techRepo.findByBusinessOwnerIdOrderByNameAsc(owner);
        List<com.Shubham.carDealership.fsm.model.FsmCustomer> customers =
                customerRepo.findByBusinessOwnerIdOrderByNameAsc(owner);

        int jobCount  = jobs.size();
        int techCount = techs.size();
        int custCount = customers.size();

        // Fire-and-forget so the HTTP response returns immediately
        CompletableFuture.runAsync(() -> {
            for (FsmJob j        : jobs)      fsmRAG.indexJob(j);
            for (FsmTechnician t : techs)     fsmRAG.indexTechnician(t);
            for (var c           : customers) fsmRAG.indexCustomer(c);
            System.out.println("FSM RAG backfill complete for owner " + owner +
                " — jobs=" + jobCount + " techs=" + techCount + " customers=" + custCount);
        });

        return ResponseEntity.ok(Map.of(
            "message",   "Backfill started in background",
            "jobs",      jobCount,
            "techs",     techCount,
            "customers", custCount,
            "total",     jobCount + techCount + custCount
        ));
    }

    @PostMapping("/chat")
    public ResponseEntity<?> chat(@RequestBody Map<String, String> body, HttpServletRequest req) {
        Long owner = ownerId(req);
        if (owner == null) return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));

        String userMessage = body.get("message");
        if (userMessage == null || userMessage.isBlank())
            return ResponseEntity.badRequest().body(Map.of("error", "Message is required"));

        String systemPrompt = buildSystemPrompt(owner);

        // Enrich with RAG context (historical jobs, customer notes, tech profiles)
        String ragContext = fsmRAG.searchRelevant(userMessage, owner, 5);
        if (ragContext != null && !ragContext.isBlank()) {
            systemPrompt = systemPrompt + "\n\n" + ragContext;
        }

        String response = openAI.chatWithContext(systemPrompt, userMessage, 300);

        return ResponseEntity.ok(Map.of("response", response));
    }

    private String buildSystemPrompt(Long ownerId) {
        StringBuilder sb = new StringBuilder();

        // Business name
        String bizName = userRepo.findById(ownerId)
                .map(u -> u.getBusinessName() != null ? u.getBusinessName() : "your business")
                .orElse("your business");

        sb.append("You are FieldFlow AI — a smart dispatch assistant for ").append(bizName).append(".\n");
        sb.append("Today: ").append(LocalDate.now()).append("\n\n");

        // Technicians
        List<FsmTechnician> techs = techRepo.findByBusinessOwnerIdOrderByNameAsc(ownerId);
        if (!techs.isEmpty()) {
            sb.append("TECHNICIANS (").append(techs.size()).append(" total):\n");
            for (FsmTechnician t : techs) {
                sb.append("- ").append(t.getName())
                  .append(": status=").append(t.getStatus());
                if (t.getSkills() != null && !t.getSkills().isBlank())
                    sb.append(", skills=").append(t.getSkills());
                sb.append("\n");
            }
            sb.append("\n");
        }

        // Today's jobs
        LocalDateTime start = LocalDate.now().atStartOfDay();
        LocalDateTime end   = start.plusDays(1);
        List<FsmJob> todayJobs = jobRepo
                .findByBusinessOwnerIdAndScheduledAtBetweenOrderByScheduledAtAsc(ownerId, start, end);
        if (!todayJobs.isEmpty()) {
            sb.append("TODAY'S JOBS (").append(todayJobs.size()).append("):\n");
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("h:mma");
            for (FsmJob j : todayJobs) {
                sb.append("- Job #").append(j.getId()).append(": ").append(j.getJobType())
                  .append(", status=").append(j.getStatus())
                  .append(", priority=").append(j.getPriority());
                if (j.getTechnician() != null)
                    sb.append(", tech=").append(j.getTechnician().getName());
                else
                    sb.append(", UNASSIGNED");
                if (j.getScheduledAt() != null)
                    sb.append(", at=").append(j.getScheduledAt().format(fmt));
                if (j.getAddress() != null)
                    sb.append(", addr=").append(j.getAddress());
                sb.append("\n");
            }
            sb.append("\n");
        } else {
            sb.append("TODAY'S JOBS: None scheduled for today.\n\n");
        }

        // Quick stats
        long activeJobs    = jobRepo.countByBusinessOwnerIdAndStatus(ownerId, "IN_PROGRESS");
        long unassigned    = jobRepo.countByBusinessOwnerIdAndTechnicianIsNull(ownerId);
        long totalCustomers = customerRepo.findByBusinessOwnerIdOrderByNameAsc(ownerId).size();
        long availableTechs = techs.stream().filter(t -> "AVAILABLE".equals(t.getStatus())).count();

        sb.append("QUICK STATS:\n");
        sb.append("- Active (in-progress) jobs: ").append(activeJobs).append("\n");
        sb.append("- Unassigned jobs: ").append(unassigned).append("\n");
        sb.append("- Available technicians: ").append(availableTechs).append("\n");
        sb.append("- Total customers: ").append(totalCustomers).append("\n\n");

        sb.append("INSTRUCTIONS:\n");
        sb.append("Help the manager with job assignments, schedule questions, performance insights, ");
        sb.append("and writing job descriptions. Be concise (under 150 words). ");
        sb.append("Use bullet points when listing items. Only discuss this business's operations.");

        return sb.toString();
    }
}
