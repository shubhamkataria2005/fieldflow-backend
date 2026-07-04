package com.Shubham.carDealership.service;

import com.fasterxml.jackson.databind.ObjectMapper;
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

    private final ObjectMapper objectMapper = new ObjectMapper();
    private boolean available = false;

    @PostConstruct
    public void init() {
        if (apiKey != null && !apiKey.isEmpty() && !apiKey.equals("your-openai-api-key-here")) {
            available = true;
            System.out.println("✅ OpenAI Service initialized (gpt-4o-mini)");
        } else {
            System.out.println("⚠️  OpenAI API key not configured.");
        }
    }

    public String chatWithContext(String systemPrompt, String userMessage, int maxTokens) {
        if (!available) return "AI features are not configured in this environment.";
        String r = callChat(systemPrompt, userMessage, maxTokens, 0.7);
        return r != null ? r : "I couldn't generate a response. Please try again.";
    }

    public String callChat(String system, String user, int maxTokens, double temp) {
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
                return ((String) ((Map<String, Object>) choices.get(0).get("message")).get("content")).trim();
            }
        } catch (Exception e) { System.err.println("❌ OpenAI: " + e.getMessage()); }
        return null;
    }

    public boolean isOpenAIAvailable() { return available; }
}
