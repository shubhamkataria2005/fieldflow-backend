package com.Shubham.carDealership.controller;

import com.Shubham.carDealership.service.CarFinderAgentService;
import com.Shubham.carDealership.service.ListingAgentService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Handles all AI agent endpoints.
 *
 * POST /api/agent/car-finder  — Agent 1: Smart Car Finder
 * POST /api/agent/generate-listing — Agent 2: Auto-Listing description generator
 */
@RestController
@RequestMapping("/api/agent")
public class AgentController {

    @Autowired
    private CarFinderAgentService carFinderAgentService;

    @Autowired
    private ListingAgentService listingAgentService;

    /**
     * Agent 1 — Car Finder
     *
     * Request body:
     * {
     *   "message": "I want a petrol SUV under $40k",
     *   "history": [                          // optional — previous turns for context
     *     { "role": "user",      "content": "..." },
     *     { "role": "assistant", "content": "..." }
     *   ]
     * }
     *
     * Response:
     * {
     *   "success": true,
     *   "message": "I found 3 SUVs that match...",
     *   "cars": [...]    // car objects — may be empty if no search was triggered
     * }
     */
    @PostMapping("/car-finder")
    public ResponseEntity<Map<String, Object>> carFinder(@RequestBody Map<String, Object> body,
                                                          HttpServletRequest request) {
        String                    userMessage = (String) body.getOrDefault("message", "");
        List<Map<String, Object>> history     = (List<Map<String, Object>>) body.get("history");

        if (userMessage == null || userMessage.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Message cannot be empty."));
        }

        // Extract JWT so the agent can book on behalf of logged-in users.
        // Optional — anonymous users can still search; they just can't book.
        String jwtToken = null;
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            jwtToken = authHeader.substring(7);
        }

        CarFinderAgentService.AgentResponse response = carFinderAgentService.chat(userMessage, history, jwtToken);

        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", response.message,
            "cars",    response.cars
        ));
    }

    /**
     * Agent 2 — Auto-Listing Description Generator
     *
     * Request body:
     * {
     *   "make": "Toyota", "model": "Camry", "year": 2022,
     *   "mileage": 45000, "fuel": "Petrol", "transmission": "Automatic",
     *   "bodyType": "Sedan", "condition": "Excellent"
     * }
     *
     * Response:
     * {
     *   "success": true,
     *   "description": "...",
     *   "suggestedPrice": 28500,
     *   "priceReason": "Based on 3 similar Camrys in our inventory averaging $29,200"
     * }
     */
    @PostMapping("/generate-listing")
    public ResponseEntity<Map<String, Object>> generateListing(@RequestBody Map<String, Object> body) {
        ListingAgentService.ListingDraft draft = listingAgentService.generate(body);

        return ResponseEntity.ok(Map.of(
            "success",        true,
            "description",    draft.description,
            "suggestedPrice", draft.suggestedPrice,
            "priceReason",    draft.priceReason
        ));
    }
}
