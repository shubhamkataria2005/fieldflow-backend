package com.Shubham.carDealership.service;

import com.theokanning.openai.service.OpenAiService;
import com.theokanning.openai.completion.CompletionRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Service
public class OpenAIService {

    @Value("${openai.api.key:}")
    private String apiKey;

    private OpenAiService openAiService;

    @Autowired
    private AIRuleService aiRuleService;

    @Autowired
    private AIDatabaseQueryService dbQueryService;

    @PostConstruct
    public void init() {
        System.out.println("🔑 Initializing OpenAI Service...");

        if (apiKey != null && !apiKey.isEmpty() && !apiKey.equals("your-openai-api-key-here")) {
            try {
                this.openAiService = new OpenAiService(apiKey, Duration.ofSeconds(60));
                System.out.println("✅ OpenAI Service initialized successfully");
            } catch (Exception e) {
                System.out.println("❌ OpenAI initialization failed: " + e.getMessage());
                this.openAiService = null;
            }
        } else {
            System.out.println("⚠️ OpenAI API key not configured. AI features will use fallback responses.");
            this.openAiService = null;
        }
    }

    public String generateResponse(String userMessage) {
        // 1. Check if query is allowed by rules
        if (!aiRuleService.isQueryAllowed(userMessage)) {
            System.out.println("🚫 Query blocked by rules");
            return aiRuleService.getRefusalMessage();
        }

        // 2. Try AI-powered database query (handles ANY question naturally)
        System.out.println("🤖 Using AI to understand: " + userMessage);
        String dbResponse = dbQueryService.handleNaturalLanguageQuery(userMessage);
        if (dbResponse != null && !dbResponse.contains("I can only help with car-related questions")) {
            System.out.println("📊 AI Database response generated");
            return dbResponse;
        }

        // 3. Check for custom responses from rules
        String customResponse = aiRuleService.getCustomResponse(userMessage);
        if (customResponse != null) {
            System.out.println("✅ Using custom response from rules");
            return customResponse;
        }

        // 4. Check legacy custom responses
        String legacyCustomResponse = getLegacyCustomResponse(userMessage);
        if (legacyCustomResponse != null) {
            System.out.println("✅ Using legacy custom response");
            return legacyCustomResponse;
        }

        // 5. Use OpenAI for general conversation
        if (openAiService != null) {
            System.out.println("🚀 Using OpenAI API for general conversation");
            return callOpenAI(userMessage);
        }

        // 6. Fallback
        return getFallbackResponse(userMessage);
    }

    // New method for AI-powered database queries
    public String generateCompletion(String prompt) {
        if (openAiService == null) {
            System.out.println("⚠️ OpenAI not available for completion");
            return null;
        }

        try {
            System.out.println("🤖 Generating AI completion...");
            CompletionRequest completionRequest = CompletionRequest.builder()
                    .model("gpt-3.5-turbo-instruct")
                    .prompt(prompt)
                    .maxTokens(500)
                    .temperature(0.3)  // Lower temperature for more deterministic SQL generation
                    .build();

            String response = openAiService.createCompletion(completionRequest)
                    .getChoices()
                    .get(0)
                    .getText()
                    .trim();

            System.out.println("✅ AI completion generated: " + response.substring(0, Math.min(100, response.length())) + "...");
            return response;

        } catch (Exception e) {
            System.err.println("❌ OpenAI completion failed: " + e.getMessage());
            return null;
        }
    }

    private String getLegacyCustomResponse(String userMessage) {
        String lower = userMessage.toLowerCase().trim();

        if (lower.contains("what is your name") || lower.contains("who are you")) {
            return "I'm CarBot - Shubham's Car Dealership AI Assistant! I help with car buying, selling, financing, and dealership services. 🚗";
        }

        if (lower.contains("what projects") || lower.contains("your projects")) {
            return "This Car Dealership Platform is Shubham's main project! Features include:\n• AI-powered car recognition\n• ML trade-in valuation\n• Marketplace & Dealership hybrid system\n• Real-time messaging\n• Test drive booking\n\nCheck the portfolio for more!";
        }

        if (lower.contains("hello") || lower.contains("hi") || lower.contains("hey")) {
            return "Hello! 👋 Welcome to Shubham's Car Dealership! Ask me about our cars, prices, test drives, or how to sell your car. What can I help you with today?";
        }

        if (lower.contains("how are you")) {
            return "I'm doing great! Ready to help you find your perfect car! What are you looking for today?";
        }

        if (lower.contains("thank")) {
            return "You're very welcome! 😊 Happy to help. Is there anything else car-related I can assist you with?";
        }

        if (lower.contains("bye") || lower.contains("goodbye")) {
            return "Goodbye! 👋 Thank you for visiting Shubham's Car Dealership. Come back anytime to browse our inventory! 🚗";
        }

        return null;
    }

    private String callOpenAI(String userMessage) {
        try {
            String systemPrompt = aiRuleService.getSystemInstructions();

            String prompt = systemPrompt + "\n\n" +
                    "User Question: " + userMessage + "\n" +
                    "Assistant:";

            CompletionRequest completionRequest = CompletionRequest.builder()
                    .model("gpt-3.5-turbo-instruct")
                    .prompt(prompt)
                    .maxTokens(300)
                    .temperature(0.7)
                    .build();

            String response = openAiService.createCompletion(completionRequest)
                    .getChoices()
                    .get(0)
                    .getText()
                    .trim();

            System.out.println("🤖 OpenAI Response: " + response);
            return response;

        } catch (Exception e) {
            System.out.println("❌ OpenAI API call failed: " + e.getMessage());
            return getFallbackResponse(userMessage);
        }
    }

    private String getFallbackResponse(String userMessage) {
        String lower = userMessage.toLowerCase();

        if (lower.contains("price") || lower.contains("cost")) {
            return "💰 Our cars range from affordable to luxury. Use the filters to find cars in your budget, or ask me for specific price ranges!";
        }
        if (lower.contains("finance")) {
            return "🏦 We offer financing through multiple partners! Use the Finance Calculator in your dashboard to estimate monthly payments based on price, down payment, and loan term.";
        }
        if (lower.contains("sell") || lower.contains("list")) {
            return "📝 Listing on Marketplace is free! Go to Dashboard → List on Marketplace, add your car details and photos, and start connecting with buyers directly.";
        }
        if (lower.contains("service") || lower.contains("maintenance")) {
            return "🔧 Our Service Center handles maintenance, repairs, inspections, and test drives. Book through Dashboard → Service Center!";
        }

        return "I'm here to help! 🚗 Ask me about:\n• Available cars and inventory\n• Prices and budget recommendations\n• Specific brands (BMW, Toyota, Tesla, etc.)\n• Test drives, trade-ins, and financing\n\nWhat would you like to know?";
    }

    public boolean isOpenAIAvailable() {
        return openAiService != null;
    }

    public Map<String, Object> getOpenAIStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("serviceAvailable", openAiService != null);
        status.put("apiKeyPresent", apiKey != null && !apiKey.isEmpty() && !apiKey.equals("your-openai-api-key-here"));
        status.put("databaseQueryEnabled", true);
        status.put("aiPowered", true);
        return status;
    }
}