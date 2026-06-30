package com.Shubham.carDealership.service;

import com.Shubham.carDealership.dto.CarResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.math.BigDecimal;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Agent 2 — Auto-Listing
 *
 * Given basic car specs, this generates:
 *   1. A professional marketing description (via GPT-4o-mini)
 *   2. A suggested price based on similar cars already in inventory
 */
@Service
public class ListingAgentService {

    @Value("${openai.api.key:}")
    private String apiKey;

    @Autowired
    private CarService carService;

    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Main method — builds context from real inventory, then calls GPT.
     */
    public ListingDraft generate(Map<String, Object> specs) {
        String make         = str(specs, "make");
        String model        = str(specs, "model");
        Integer year        = num(specs, "year");
        Integer mileage     = num(specs, "mileage");
        String fuel         = str(specs, "fuel");
        String transmission = str(specs, "transmission");
        String bodyType     = str(specs, "bodyType");

        // Find similar cars in our inventory for price benchmarking
        List<CarResponse> similar = carService.searchCars(make, bodyType, fuel, null, null)
            .stream()
            .filter(c -> c.getYear() != null && year != null && Math.abs(c.getYear() - year) <= 3)
            .limit(5)
            .collect(Collectors.toList());

        double suggestedPrice = computePrice(similar, make, year);
        String priceReason    = buildPriceReason(similar, suggestedPrice);

        String description = apiKey != null && !apiKey.isBlank()
            ? callGpt(make, model, year, mileage, fuel, transmission, bodyType, similar, suggestedPrice)
            : buildFallbackDescription(make, model, year, mileage, fuel, transmission, bodyType);

        return new ListingDraft(description, suggestedPrice, priceReason);
    }

    // ── Price logic ───────────────────────────────────────────────────────────

    private double computePrice(List<CarResponse> similar, String make, Integer year) {
        if (!similar.isEmpty()) {
            double avg = similar.stream()
                .mapToDouble(c -> c.getPrice().doubleValue())
                .average()
                .orElse(0);
            // Slight discount vs average (dealership wants competitive pricing)
            return Math.round(avg * 0.97 / 500.0) * 500;
        }
        // Fallback: rough brand-tier estimate
        return brandBasePrice(make, year);
    }

    private double brandBasePrice(String make, Integer year) {
        if (make == null) return 25000;
        int age = (year != null) ? (2026 - year) : 5;
        double base = switch (make.toLowerCase()) {
            case "ferrari", "lamborghini", "mclaren", "rolls royce", "bentley" -> 250000;
            case "porsche", "aston martin" -> 120000;
            case "bmw", "mercedes", "audi" -> 55000;
            case "toyota", "honda", "mazda", "subaru" -> 28000;
            case "hyundai", "kia", "volkswagen", "nissan" -> 24000;
            case "ford" -> 26000;
            default -> 25000;
        };
        return Math.max(8000, base - (age * 2500));
    }

    private String buildPriceReason(List<CarResponse> similar, double suggested) {
        if (similar.isEmpty()) return "Estimated based on market data for this make and age.";
        double avg = similar.stream().mapToDouble(c -> c.getPrice().doubleValue()).average().orElse(0);
        return String.format(
            "Based on %d similar vehicle%s in our inventory averaging $%,.0f — priced %s% to stay competitive.",
            similar.size(),
            similar.size() == 1 ? "" : "s",
            avg,
            suggested < avg ? "3" : "at market"
        );
    }

    // ── GPT description generation ────────────────────────────────────────────

    private String callGpt(String make, String model, Integer year, Integer mileage,
                            String fuel, String transmission, String bodyType,
                            List<CarResponse> similar, double suggestedPrice) {
        String comparables = similar.stream()
            .map(c -> String.format("%d %s %s at $%,.0f", c.getYear(), c.getMake(), c.getModel(), c.getPrice().doubleValue()))
            .collect(Collectors.joining(", "));

        String userPrompt = String.format(
            "Write a compelling 2-3 sentence car listing description for:\n" +
            "  %d %s %s | %s | %s | %s | %,d km | Suggested price: $%,.0f NZD\n\n" +
            "Comparable cars in our inventory: %s\n\n" +
            "Requirements:\n" +
            "- Professional, enthusiastic tone (like a premium dealership)\n" +
            "- Highlight the key selling points for this type of car\n" +
            "- Mention the fuel type and transmission naturally\n" +
            "- No price in the description\n" +
            "- 2-3 sentences max, no bullet points",
            year, make, model, bodyType, fuel, transmission, mileage, suggestedPrice,
            comparables.isEmpty() ? "none currently" : comparables
        );

        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", "gpt-4o-mini");
            body.put("max_tokens", 200);
            body.put("temperature", 0.7);
            body.put("messages", List.of(
                Map.of("role", "system", "content",
                    "You write concise, professional car listing descriptions for a premium Auckland dealership. " +
                    "Be engaging but not over-the-top. No emojis."),
                Map.of("role", "user", "content", userPrompt)
            ));

            String reqJson = mapper.writeValueAsString(body);
            URL url = new URL("https://api.openai.com/v1/chat/completions");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type",  "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
            conn.setDoOutput(true);
            conn.setConnectTimeout(15_000);
            conn.setReadTimeout(30_000);
            try (OutputStream os = conn.getOutputStream()) { os.write(reqJson.getBytes("utf-8")); }

            if (conn.getResponseCode() != 200) {
                return buildFallbackDescription(make, model, year, mileage, fuel, transmission, bodyType);
            }

            BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "utf-8"));
            StringBuilder  sb = new StringBuilder();
            String         ln;
            while ((ln = br.readLine()) != null) sb.append(ln);
            br.close();

            Map<String, Object>       resp    = mapper.readValue(sb.toString(), Map.class);
            List<Map<String, Object>> choices = (List<Map<String, Object>>) resp.get("choices");
            return ((String) ((Map<String, Object>) choices.get(0).get("message")).get("content")).trim();

        } catch (Exception e) {
            System.err.println("❌ ListingAgent GPT error: " + e.getMessage());
            return buildFallbackDescription(make, model, year, mileage, fuel, transmission, bodyType);
        }
    }

    // Fallback if OpenAI is unavailable
    private String buildFallbackDescription(String make, String model, Integer year,
                                             Integer mileage, String fuel, String transmission, String bodyType) {
        return String.format(
            "This %d %s %s is a well-maintained %s offered with a %s engine and %s transmission. " +
            "With only %,d km on the clock, it represents excellent value and is ready to drive away today. " +
            "Available now at Shubham's Car Dealership — book a test drive and experience it for yourself.",
            year, make, model, bodyType.toLowerCase(), fuel.toLowerCase(), transmission.toLowerCase(), mileage
        );
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String str(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v != null ? v.toString() : "";
    }

    private Integer num(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v == null) return null;
        if (v instanceof Number) return ((Number) v).intValue();
        try { return Integer.parseInt(v.toString()); } catch (Exception e) { return null; }
    }

    // ── Response DTO ─────────────────────────────────────────────────────────

    public static class ListingDraft {
        public final String description;
        public final double suggestedPrice;
        public final String priceReason;

        public ListingDraft(String description, double suggestedPrice, String priceReason) {
            this.description    = description;
            this.suggestedPrice = suggestedPrice;
            this.priceReason    = priceReason;
        }
    }
}
