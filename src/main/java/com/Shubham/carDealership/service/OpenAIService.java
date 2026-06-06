package com.Shubham.carDealership.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;

@Service
public class OpenAIService {

    @Value("${openai.api.key:}")
    private String apiKey;

    @Autowired
    private AIRuleService aiRuleService;

    @Autowired
    private AIDatabaseQueryService dbQueryService;

    @Autowired
    private RAGService ragService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private boolean available = false;

    @PostConstruct
    public void init() {
        if (apiKey != null && !apiKey.isEmpty() && !apiKey.equals("your-openai-api-key-here")) {
            available = true;
            dbQueryService.setOpenAIService(this);
            System.out.println("✅ OpenAI Service initialized (GPT-4o-mini)");
        } else {
            System.out.println("⚠️ OpenAI API key not configured.");
        }
    }

    public String generateResponse(String userMessage) {
        // 1. Rule check
        if (!aiRuleService.isQueryAllowed(userMessage)) return aiRuleService.getRefusalMessage();

        // 2. Custom responses
        String custom = aiRuleService.getCustomResponse(userMessage);
        if (custom != null) return custom;

        // 3. RAG — semantic search over inventory (TRUE RAG)
        if (ragService.isAvailable()) {
            String ragAnswer = ragService.answer(userMessage);
            if (ragAnswer != null && !ragAnswer.isEmpty()) return ragAnswer;
        }

        // 4. Text-to-SQL fallback
        String sqlAnswer = dbQueryService.handleNaturalLanguageQuery(userMessage);
        if (sqlAnswer != null && !sqlAnswer.contains("not initialized")) return sqlAnswer;

        // 5. General GPT fallback
        if (available) {
            String r = callChat("You are a helpful car dealership assistant for Shubham's Car Dealership, Auckland NZ. Answer concisely under 100 words, car topics only.", userMessage, 150, 0.7);
            if (r != null) return r;
        }

        return "I'm here to help! Ask me about our cars, prices, test drives, or trade-ins. 🚗";
    }

    public String generateCompletion(String prompt) {
        if (!available) return null;
        return callChat("You are a SQL expert. Return ONLY a valid SQL query — no explanation, no markdown.", prompt, 300, 0.1);
    }

    private String callChat(String system, String user, int maxTokens, double temp) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("model", "gpt-4o-mini");
            body.put("max_tokens", maxTokens);
            body.put("temperature", temp);
            body.put("messages", List.of(
                    Map.of("role", "system", "content", system),
                    Map.of("role", "user",   "content", user)
            ));
            String json = objectMapper.writeValueAsString(body);
            URL url = new URL("https://api.openai.com/v1/chat/completions");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
            conn.setDoOutput(true);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(30000);
            try (OutputStream os = conn.getOutputStream()) { os.write(json.getBytes("utf-8")); }
            if (conn.getResponseCode() == 200) {
                BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "utf-8"));
                StringBuilder sb = new StringBuilder(); String line;
                while ((line = br.readLine()) != null) sb.append(line); br.close();
                Map<String, Object> result = objectMapper.readValue(sb.toString(), Map.class);
                List<Map<String, Object>> choices = (List<Map<String, Object>>) result.get("choices");
                return ((String)((Map<String,Object>)choices.get(0).get("message")).get("content")).trim();
            }
        } catch (Exception e) { System.err.println("❌ OpenAI: " + e.getMessage()); }
        return null;
    }

    public boolean isOpenAIAvailable() { return available; }

    public Map<String, Object> getOpenAIStatus() {
        return Map.of("serviceAvailable", available, "ragAvailable", ragService.isAvailable(), "model", "gpt-4o-mini");
    }
}