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

                // Set the OpenAIService reference in AIDatabaseQueryService
                dbQueryService.setOpenAIService(this);
                System.out.println("✅ Database query service connected to OpenAI");

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
        System.out.println("💬 User: " + userMessage);

        // 1. Check rules
        if (!aiRuleService.isQueryAllowed(userMessage)) {
            return aiRuleService.getRefusalMessage();
        }

        // 2. Check custom responses
        String customResponse = aiRuleService.getCustomResponse(userMessage);
        if (customResponse != null) {
            return customResponse;
        }

        // 3. Use AI database query (TRUE AI!)
        String aiResponse = dbQueryService.handleNaturalLanguageQuery(userMessage);
        if (aiResponse != null && !aiResponse.contains("couldn't understand")) {
            return aiResponse;
        }

        // 4. Fallback for non-database questions
        if (openAiService != null) {
            return callOpenAI(userMessage);
        }

        return "I'm here to help! Ask me about cars, prices, test drives, or how to sell your car. 🚗";
    }

    public String generateCompletion(String prompt) {
        if (openAiService == null) {
            System.out.println("⚠️ OpenAI not available");
            return null;
        }

        try {
            CompletionRequest request = CompletionRequest.builder()
                    .model("gpt-3.5-turbo-instruct")
                    .prompt(prompt)
                    .maxTokens(300)
                    .temperature(0.1)
                    .build();

            String response = openAiService.createCompletion(request)
                    .getChoices()
                    .get(0)
                    .getText()
                    .trim();

            System.out.println("🤖 OpenAI → " + response);
            return response;

        } catch (Exception e) {
            System.err.println("❌ OpenAI error: " + e.getMessage());
            return null;
        }
    }

    private String callOpenAI(String userMessage) {
        try {
            String prompt = "You are a helpful car dealership assistant. Answer concisely.\n\nUser: " + userMessage + "\nAssistant:";

            CompletionRequest request = CompletionRequest.builder()
                    .model("gpt-3.5-turbo-instruct")
                    .prompt(prompt)
                    .maxTokens(150)
                    .temperature(0.7)
                    .build();

            return openAiService.createCompletion(request)
                    .getChoices()
                    .get(0)
                    .getText()
                    .trim();

        } catch (Exception e) {
            return "I'm here to help! Ask me about cars, prices, test drives, or how to sell your car. 🚗";
        }
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