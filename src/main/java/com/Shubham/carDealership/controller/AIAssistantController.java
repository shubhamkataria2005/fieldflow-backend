package com.Shubham.carDealership.controller;

import com.Shubham.carDealership.service.AIAssistantService;
import com.Shubham.carDealership.service.AIRuleService;
import com.Shubham.carDealership.service.OpenAIService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/ai-assistant")
@CrossOrigin(origins = {"http://localhost:5173", "http://localhost:3000", "https://ai-car-dealership-frontend.onrender.com"})
public class AIAssistantController {

    @Autowired
    private AIAssistantService aiAssistantService;

    @Autowired
    private AIRuleService aiRuleService;

    @Autowired
    private OpenAIService openAIService;

    @PostMapping("/chat")
    public ResponseEntity<?> chat(@RequestBody Map<String, String> request) {
        String userMessage = request.get("message");

        if (userMessage == null || userMessage.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Message cannot be empty"));
        }

        long startTime = System.currentTimeMillis();
        String response = aiAssistantService.getResponse(userMessage);
        long endTime = System.currentTimeMillis();

        System.out.println("⏱️ AI Response time: " + (endTime - startTime) + "ms");

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("response", response);
        result.put("responseTime", (endTime - startTime));

        return ResponseEntity.ok(result);
    }

    @GetMapping("/test-rules")
    public ResponseEntity<?> testRules(@RequestParam String message) {
        boolean allowed = aiRuleService.isQueryAllowed(message);
        String customResponse = aiRuleService.getCustomResponse(message);

        Map<String, Object> response = new HashMap<>();
        response.put("message", message);
        response.put("isAllowed", allowed);
        response.put("customResponse", customResponse);

        if (!allowed) {
            response.put("aiResponse", aiRuleService.getRefusalMessage());
        } else if (customResponse != null) {
            response.put("aiResponse", customResponse);
        } else {
            response.put("aiResponse", "This would go to AI-powered database query or OpenAI");
        }

        return ResponseEntity.ok(response);
    }

    @GetMapping("/status")
    public ResponseEntity<?> getStatus() {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("openAIStatus", openAIService.getOpenAIStatus());
        response.put("aiPoweredDatabaseQueries", true);
        response.put("message", "AI Assistant is running with true AI-powered database queries!");
        return ResponseEntity.ok(response);
    }

    @GetMapping("/debug/schema")
    public ResponseEntity<?> getSchema() {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "AI Database Query Service is active");
        response.put("note", "This uses OpenAI to convert natural language to SQL queries");
        response.put("capabilities", new String[]{
                "Natural language to SQL conversion",
                "Handle typos and misspellings",
                "Complex queries (counts, listings, price ranges, comparisons)",
                "Context-aware responses"
        });
        return ResponseEntity.ok(response);
    }
}