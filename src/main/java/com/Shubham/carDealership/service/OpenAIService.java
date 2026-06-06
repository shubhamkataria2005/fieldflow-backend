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

        // 2. Try database query (no circular dependency)
        String dbResponse = dbQueryService.handleNaturalLanguageQuery(userMessage);
        if (dbResponse != null) {
            System.out.println("📊 Database response for: " + userMessage);
            return dbResponse;
        }

        // 3. Check for custom responses
        String customResponse = aiRuleService.getCustomResponse(userMessage);
        if (customResponse != null) {
            System.out.println("✅ Using custom response");
            return customResponse;
        }

        // 4. Use OpenAI for complex questions
        if (openAiService != null) {
            System.out.println("🚀 Using OpenAI API");
            return callOpenAI(userMessage);
        }

        // 5. Fallback
        return getFallbackResponse(userMessage);
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
        return status;
    }
}