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
        // Check rules first
        if (!aiRuleService.isQueryAllowed(userMessage)) {
            return aiRuleService.getRefusalMessage();
        }

        // Check custom responses
        String customResponse = aiRuleService.getCustomResponse(userMessage);
        if (customResponse != null) {
            return customResponse;
        }

        // Use OpenAI
        if (openAiService != null) {
            return callOpenAI(userMessage);
        }

        return getFallbackResponse(userMessage);
    }

    // Method for AI-powered database queries
    public String generateCompletion(String prompt) {
        if (openAiService == null) {
            System.out.println("⚠️ OpenAI not available for completion");
            return null;
        }

        try {
            CompletionRequest request = CompletionRequest.builder()
                    .model("gpt-3.5-turbo-instruct")
                    .prompt(prompt)
                    .maxTokens(300)
                    .temperature(0.1)  // Low temperature for consistent SQL
                    .build();

            String response = openAiService.createCompletion(request)
                    .getChoices()
                    .get(0)
                    .getText()
                    .trim();

            System.out.println("🤖 AI Completion: " + response);
            return response;

        } catch (Exception e) {
            System.err.println("❌ Completion failed: " + e.getMessage());
            return null;
        }
    }

    private String callOpenAI(String userMessage) {
        try {
            String systemPrompt = aiRuleService.getSystemInstructions();

            String prompt = systemPrompt + "\n\nUser: " + userMessage + "\nAssistant:";

            CompletionRequest request = CompletionRequest.builder()
                    .model("gpt-3.5-turbo-instruct")
                    .prompt(prompt)
                    .maxTokens(300)
                    .temperature(0.7)
                    .build();

            String response = openAiService.createCompletion(request)
                    .getChoices()
                    .get(0)
                    .getText()
                    .trim();

            System.out.println("🤖 OpenAI Response: " + response);
            return response;

        } catch (Exception e) {
            System.err.println("❌ OpenAI call failed: " + e.getMessage());
            return getFallbackResponse(userMessage);
        }
    }

    private String getFallbackResponse(String userMessage) {
        String lower = userMessage.toLowerCase();

        if (lower.contains("price") || lower.contains("cost")) {
            return "💰 Our cars range from affordable to luxury. Check the inventory for specific prices!";
        }
        if (lower.contains("finance")) {
            return "🏦 We offer financing options! Use the Finance Calculator in your dashboard.";
        }
        if (lower.contains("sell") || lower.contains("list")) {
            return "📝 To sell your car, go to Dashboard → List on Marketplace. It's free!";
        }
        if (lower.contains("test drive")) {
            return "🚗 Book a test drive through Dashboard → Service Center!";
        }
        if (lower.contains("trade")) {
            return "🔄 Trade in your car via Dashboard → Trade-In section!";
        }

        return "I'm here to help! Ask me about cars, prices, test drives, or how to sell your car. 🚗";
    }

    public boolean isOpenAIAvailable() {
        return openAiService != null;
    }

    public Map<String, Object> getOpenAIStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("serviceAvailable", openAiService != null);
        status.put("apiKeyPresent", apiKey != null && !apiKey.isEmpty());
        status.put("apiKeyConfigured", apiKey != null && !apiKey.equals("your-openai-api-key-here"));
        return status;
    }
}