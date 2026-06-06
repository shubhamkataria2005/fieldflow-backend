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

    // NEW: Method for database query generation
    public String generateCompletion(String prompt) {
        if (openAiService == null) {
            return null;
        }

        try {
            CompletionRequest request = CompletionRequest.builder()
                    .model("gpt-3.5-turbo-instruct")
                    .prompt(prompt)
                    .maxTokens(300)
                    .temperature(0.1)  // Low temperature for consistent SQL
                    .build();

            return openAiService.createCompletion(request)
                    .getChoices()
                    .get(0)
                    .getText()
                    .trim();
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

            return openAiService.createCompletion(request)
                    .getChoices()
                    .get(0)
                    .getText()
                    .trim();

        } catch (Exception e) {
            return getFallbackResponse(userMessage);
        }
    }

    private String getFallbackResponse(String userMessage) {
        return "I'm here to help! Ask me about cars, prices, or specific brands. 🚗";
    }

    public boolean isOpenAIAvailable() {
        return openAiService != null;
    }
}