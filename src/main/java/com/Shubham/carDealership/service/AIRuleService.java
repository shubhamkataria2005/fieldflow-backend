package com.Shubham.carDealership.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class AIRuleService {

    @Value("classpath:ai-assistant-instructions.md")
    private Resource instructionsFile;

    private String systemInstructions;
    private List<String> allowedKeywords;
    private List<String> blockedKeywords;

    @PostConstruct
    public void init() {
        loadInstructions();
        initializeKeywords();
        System.out.println("✅ AI Rules Service initialized!");
        System.out.println("   - Allowed topics: " + allowedKeywords.size());
        System.out.println("   - Blocked topics: " + blockedKeywords.size());
    }

    private void loadInstructions() {
        try {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(instructionsFile.getInputStream(), StandardCharsets.UTF_8))) {
                systemInstructions = reader.lines()
                        .collect(Collectors.joining("\n"));
                System.out.println("✅ Loaded AI instructions from file");
            }
        } catch (Exception e) {
            System.err.println("⚠️ Failed to load AI instructions: " + e.getMessage());
            systemInstructions = "You are a helpful car dealership assistant.";
        }
    }

    private void initializeKeywords() {
        // Keywords that are ALLOWED (car-related topics)
        allowedKeywords = Arrays.asList(
                "car", "vehicle", "buy", "sell", "trade", "price", "cost",
                "finance", "loan", "payment", "service", "test drive",
                "maintenance", "repair", "inspection", "warranty",
                "model", "make", "brand", "bmw", "toyota", "honda", "tesla",
                "dealership", "marketplace", "appointment", "booking",
                "shubham", "portfolio", "valuation", "mileage", "engine",
                "available", "stock", "inventory", "purchase"
        );

        // Keywords that are BLOCKED (non-car topics)
        blockedKeywords = Arrays.asList(
                "politics", "election", "president", "prime minister",
                "religion", "god", "jesus", "allah", "buddha", "church",
                "medical", "doctor", "hospital", "medicine", "health",
                "legal", "lawyer", "attorney", "lawsuit",
                "hack", "crack", "illegal", "steal", "cheat",
                "sex", "porn", "dating", "romance",
                "gambling", "casino", "lottery", "bet"
        );
    }

    public boolean isQueryAllowed(String userMessage) {
        String lowerMessage = userMessage.toLowerCase();

        for (String blocked : blockedKeywords) {
            if (lowerMessage.contains(blocked)) {
                System.out.println("🚫 Query blocked: contains '" + blocked + "'");
                return false;
            }
        }

        for (String allowed : allowedKeywords) {
            if (lowerMessage.contains(allowed)) {
                System.out.println("✅ Query allowed: contains '" + allowed + "'");
                return true;
            }
        }

        if (lowerMessage.matches(".*\\b(hello|hi|hey|thanks|thank you|bye|goodbye)\\b.*")) {
            System.out.println("✅ Query allowed: greeting/thanks");
            return true;
        }

        System.out.println("⚠️ Query unclear, allowing but will use fallback: " + userMessage);
        return true;
    }

    public String getRefusalMessage() {
        return "I'm sorry, but I can only help with car-related questions at Shubham's Car Dealership. " +
                "Please ask me about buying cars, selling on marketplace, test drives, or trade-ins! 🚗";
    }

    public String getSystemInstructions() {
        return systemInstructions;
    }

    public String getCustomResponse(String userMessage) {
        String lower = userMessage.toLowerCase();

        if (lower.contains("shubham") || lower.contains("portfolio")) {
            return "Check out Shubham's amazing portfolio at https://shubhamkataria2005.github.io/Shubham_Portfolio/";
        }

        if (lower.contains("what can you help") || lower.contains("what can you do")) {
            return "I can help you find cars, check prices, explain how to buy/sell, book test drives, and answer questions about our dealership services! What would you like to know?";
        }

        if (lower.contains("test drive")) {
            return "You can book a test drive through the Service Center in your dashboard! Just go to Dashboard → Service Center → Book Test Drive. 🚗";
        }

        if (lower.contains("trade in") || lower.contains("trade-in")) {
            return "Visit the Trade-In section in your dashboard to get an instant valuation for your car! We offer competitive prices. 💰";
        }

        if (lower.contains("finance") || lower.contains("loan")) {
            return "We offer financing options through our partners. Use the Finance Calculator in your dashboard to estimate monthly payments! 📊";
        }

        if (lower.contains("hour") || lower.contains("open") || lower.contains("timing")) {
            return "Our dealership is open Monday to Friday, 9 AM - 6 PM, and Saturday 10 AM - 4 PM. We're closed on Sundays.";
        }

        return null;
    }
}