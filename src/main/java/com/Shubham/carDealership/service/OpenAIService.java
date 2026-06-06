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
        // FIRST: Check if query is allowed by rules
        if (!aiRuleService.isQueryAllowed(userMessage)) {
            System.out.println("🚫 Query blocked by rules");
            return aiRuleService.getRefusalMessage();
        }

        // SECOND: Check for custom responses from rules
        String customResponse = aiRuleService.getCustomResponse(userMessage);
        if (customResponse != null) {
            System.out.println("✅ Using custom response from rules");
            return customResponse;
        }

        // THIRD: Check legacy custom responses (for backward compatibility)
        String legacyCustomResponse = getLegacyCustomResponse(userMessage);
        if (legacyCustomResponse != null) {
            System.out.println("✅ Using legacy custom response");
            return legacyCustomResponse;
        }

        // FOURTH: Use OpenAI for other questions
        if (openAiService != null) {
            System.out.println("🚀 Using OpenAI API");
            return callOpenAI(userMessage);
        }

        // FIFTH: Fallback if OpenAI not available
        return getFallbackResponse(userMessage);
    }

    private String getLegacyCustomResponse(String userMessage) {
        String lower = userMessage.toLowerCase().trim();

        if (lower.contains("what is your name") || lower.contains("who are you")) {
            return "I'm Shubham's Car Dealership AI Assistant! I help with car buying, financing, and dealership services. 🚗";
        }

        if (lower.contains("what projects") || lower.contains("your projects")) {
            return "This Car Dealership Platform is one of Shubham's main projects! It features Marketplace, Dealership, AI tools, and more. Check the portfolio for other projects!";
        }

        if (lower.contains("marketplace") || lower.contains("private seller")) {
            return "Our Marketplace allows users to buy and sell cars directly with other users. You can list your car for sale and message sellers directly!";
        }

        if (lower.contains("dealership") || lower.contains("company car")) {
            return "Our Dealership section features company-owned, professionally inspected cars with test drive options and service center access.";
        }

        if (lower.contains("hello") || lower.contains("hi") || lower.contains("hey")) {
            return "Hello! How can I help you with car buying or selling today?";
        }

        if (lower.contains("how are you")) {
            return "I'm doing great! Ready to help you find your perfect car!";
        }

        if (lower.contains("thank")) {
            return "You're welcome! Happy to help!";
        }

        if (lower.contains("bye") || lower.contains("goodbye")) {
            return "Goodbye! Come back anytime to browse our cars!";
        }

        return null;
    }

    private String callOpenAI(String userMessage) {
        try {
            String systemPrompt = aiRuleService.getSystemInstructions();

            String prompt = systemPrompt + "\n\n" +
                    "User Question: " + userMessage + "\n" +
                    "Assistant Response:";

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
            return "Our cars range from $15,000 to $100,000+. Check the inventory for specific prices!";
        }
        if (lower.contains("finance")) {
            return "We offer financing options through our partners. Use the Finance Calculator in your dashboard to estimate payments!";
        }
        if (lower.contains("sell") || lower.contains("list")) {
            return "To sell your car, go to Dashboard and click 'List on Marketplace'. It's free!";
        }
        if (lower.contains("service") || lower.contains("maintenance")) {
            return "Visit the Service Center in your dashboard to book test drives and service appointments.";
        }

        return "I'm here to help! You can ask me about cars, financing, test drives, or how to use our platform. What would you like to know?";
    }

    public boolean isOpenAIAvailable() {
        return openAiService != null;
    }

    public Map<String, Object> getOpenAIStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("serviceAvailable", openAiService != null);
        status.put("apiKeyPresent", apiKey != null && !apiKey.isEmpty() && !apiKey.equals("your-openai-api-key-here"));
        return status;
    }
}