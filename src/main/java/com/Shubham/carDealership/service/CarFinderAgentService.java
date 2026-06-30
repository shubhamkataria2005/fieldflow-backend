package com.Shubham.carDealership.service;

import com.Shubham.carDealership.dto.CarResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Agent 1 — Smart Car Finder
 *
 * Uses OpenAI function calling (tool use) so GPT can query our real inventory
 * before generating a response. Flow:
 *   User message → GPT decides to call search_cars/get_car_details
 *   → we run the query → send results back to GPT → GPT writes final reply
 *
 * This "agentic loop" means the AI always talks about cars that actually exist.
 */
@Service
public class CarFinderAgentService {

    @Value("${openai.api.key:}")
    private String apiKey;

    @Autowired
    private CarService carService;

    private final ObjectMapper mapper = new ObjectMapper();

    private static final String SYSTEM_PROMPT =
        "You are a smart car-finding assistant for Shubham's Car Dealership in Auckland, NZ. " +
        "Help customers find the perfect car from our live inventory.\n\n" +
        "Rules:\n" +
        "- Always use search_cars before describing any cars — never invent vehicles.\n" +
        "- After a search, summarise what you found in 2-3 sentences, mention key highlights (price, mileage, fuel).\n" +
        "- Offer to book a test drive or get more details on any result.\n" +
        "- If nothing matches, suggest adjusting the budget or trying a different body type.\n" +
        "- Keep every reply under 120 words.\n" +
        "- Stick to car and dealership topics only.";

    // ── Tool definitions sent to OpenAI ──────────────────────────────────────

    private List<Map<String, Object>> buildTools() {
        // Tool 1: search_cars
        Map<String, Object> searchParams = new LinkedHashMap<>();
        searchParams.put("keyword",  Map.of("type", "string",  "description", "Free-text keyword: make, model, year, or description"));
        searchParams.put("make",     Map.of("type", "string",  "description", "Car brand, e.g. Toyota, BMW, Honda"));
        searchParams.put("bodyType", Map.of("type", "string",  "description", "Sedan | SUV | Hatchback | Ute | Wagon | Coupe | Convertible"));
        searchParams.put("fuel",     Map.of("type", "string",  "description", "Petrol | Diesel | Hybrid | Electric"));
        searchParams.put("maxPrice", Map.of("type", "number",  "description", "Maximum price in NZD"));
        searchParams.put("minYear",  Map.of("type", "integer", "description", "Minimum manufacture year, e.g. 2018"));

        Map<String, Object> searchFn = new LinkedHashMap<>();
        searchFn.put("name", "search_cars");
        searchFn.put("description", "Search available cars in our live inventory based on customer preferences. Call this first whenever a customer describes what they want.");
        searchFn.put("parameters", Map.of("type", "object", "properties", searchParams, "required", List.of()));

        // Tool 2: get_car_details
        Map<String, Object> detailParams = Map.of(
            "carId", Map.of("type", "integer", "description", "The numeric ID of the car")
        );
        Map<String, Object> detailFn = new LinkedHashMap<>();
        detailFn.put("name", "get_car_details");
        detailFn.put("description", "Fetch full details of a specific car (description, inspection status, seller info) by its ID.");
        detailFn.put("parameters", Map.of("type", "object", "properties", detailParams, "required", List.of("carId")));

        return List.of(
            Map.of("type", "function", "function", searchFn),
            Map.of("type", "function", "function", detailFn)
        );
    }

    // ── Tool execution — these call our real Spring services ─────────────────

    private String executeTool(String toolName, String argsJson) {
        try {
            Map<String, Object> args = mapper.readValue(argsJson, Map.class);

            if ("search_cars".equals(toolName)) {
                String keyword  = (String)  args.getOrDefault("keyword",  null);
                String make     = (String)  args.getOrDefault("make",     null);
                String bodyType = (String)  args.getOrDefault("bodyType", null);
                String fuel     = (String)  args.getOrDefault("fuel",     null);
                Double maxPrice = args.get("maxPrice") != null ? ((Number) args.get("maxPrice")).doubleValue() : null;
                Integer minYear = args.get("minYear")  != null ? ((Number) args.get("minYear")).intValue()    : null;

                List<CarResponse> cars = carService.searchCars(make, bodyType, fuel, maxPrice, null);

                // Client-side keyword filter (handles partial matches)
                if (keyword != null && !keyword.isBlank()) {
                    String kw = keyword.toLowerCase();
                    cars = cars.stream().filter(c ->
                        contains(c.getMake(), kw) || contains(c.getModel(), kw) ||
                        contains(c.getDescription(), kw) || String.valueOf(c.getYear()).contains(kw)
                    ).collect(Collectors.toList());
                }

                // Year floor
                if (minYear != null) {
                    int yr = minYear;
                    cars = cars.stream().filter(c -> c.getYear() != null && c.getYear() >= yr).collect(Collectors.toList());
                }

                // Cap at 5 results — GPT doesn't need more than that
                if (cars.size() > 5) cars = cars.subList(0, 5);

                if (cars.isEmpty()) {
                    return mapper.writeValueAsString(Map.of("found", 0, "message", "No cars matched those criteria."));
                }

                List<Map<String, Object>> summaries = new ArrayList<>();
                for (CarResponse c : cars) {
                    Map<String, Object> s = new LinkedHashMap<>();
                    s.put("id",           c.getId());
                    s.put("make",         c.getMake());
                    s.put("model",        c.getModel());
                    s.put("year",         c.getYear());
                    s.put("price",        c.getPrice());
                    s.put("mileage",      c.getMileage() + " km");
                    s.put("fuel",         c.getFuel());
                    s.put("transmission", c.getTransmission());
                    s.put("bodyType",     c.getBodyType());
                    s.put("source",       c.getCarSource());
                    s.put("inspected",    "PASSED".equals(c.getInspectionStatus()));
                    summaries.add(s);
                }
                return mapper.writeValueAsString(Map.of("found", cars.size(), "cars", summaries));

            } else if ("get_car_details".equals(toolName)) {
                Long carId = ((Number) args.get("carId")).longValue();
                CarResponse c = carService.getCarById(carId);
                Map<String, Object> s = new LinkedHashMap<>();
                s.put("id",           c.getId());
                s.put("make",         c.getMake());
                s.put("model",        c.getModel());
                s.put("year",         c.getYear());
                s.put("price",        c.getPrice());
                s.put("mileage",      c.getMileage() + " km");
                s.put("fuel",         c.getFuel());
                s.put("transmission", c.getTransmission());
                s.put("bodyType",     c.getBodyType());
                s.put("description",  c.getDescription());
                s.put("source",       c.getCarSource());
                s.put("inspected",    "PASSED".equals(c.getInspectionStatus()));
                return mapper.writeValueAsString(s);
            }

        } catch (Exception e) {
            System.err.println("❌ Agent tool error [" + toolName + "]: " + e.getMessage());
            return "{\"error\":\"" + e.getMessage() + "\"}";
        }
        return "{\"error\":\"unknown tool\"}";
    }

    // ── Main entry point ─────────────────────────────────────────────────────

    /**
     * Run one turn of the agent conversation.
     *
     * @param userMessage  The latest message from the user.
     * @param history      Previous turns in OpenAI message format so the agent
     *                     remembers context (passed from the frontend).
     * @return AgentResponse with the text reply + any car objects to display.
     */
    public AgentResponse chat(String userMessage, List<Map<String, Object>> history) {
        if (apiKey == null || apiKey.isBlank()) {
            return new AgentResponse("AI agent is not configured — please add an OpenAI API key.", List.of());
        }

        // Build message list: system + prior history + new user turn
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", SYSTEM_PROMPT));
        if (history != null) messages.addAll(history);
        messages.add(Map.of("role", "user", "content", userMessage));

        List<Map<String, Object>> tools       = buildTools();
        List<CarResponse>         foundCars   = new ArrayList<>();

        // Agentic loop — GPT may call tools multiple times before settling on a reply.
        // We cap at 3 rounds to prevent infinite loops.
        for (int round = 0; round < 3; round++) {
            try {
                Map<String, Object> requestBody = new LinkedHashMap<>();
                requestBody.put("model",       "gpt-4o-mini");
                requestBody.put("messages",    messages);
                requestBody.put("tools",       tools);
                requestBody.put("tool_choice", "auto");
                requestBody.put("max_tokens",  500);
                requestBody.put("temperature", 0.5);

                String reqJson = mapper.writeValueAsString(requestBody);

                URL               url  = new URL("https://api.openai.com/v1/chat/completions");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type",  "application/json");
                conn.setRequestProperty("Authorization", "Bearer " + apiKey);
                conn.setDoOutput(true);
                conn.setConnectTimeout(15_000);
                conn.setReadTimeout(30_000);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(reqJson.getBytes("utf-8"));
                }

                if (conn.getResponseCode() != 200) {
                    System.err.println("❌ OpenAI HTTP " + conn.getResponseCode());
                    return new AgentResponse("I'm having trouble reaching the AI. Please try again.", List.of());
                }

                BufferedReader br  = new BufferedReader(new InputStreamReader(conn.getInputStream(), "utf-8"));
                StringBuilder  sb  = new StringBuilder();
                String         ln;
                while ((ln = br.readLine()) != null) sb.append(ln);
                br.close();

                Map<String, Object>       response     = mapper.readValue(sb.toString(), Map.class);
                List<Map<String, Object>> choices      = (List<Map<String, Object>>) response.get("choices");
                Map<String, Object>       choiceObj    = choices.get(0);
                Map<String, Object>       assistantMsg = (Map<String, Object>) choiceObj.get("message");
                String                    finishReason = (String) choiceObj.get("finish_reason");

                // Always add the assistant turn back into the conversation
                messages.add(assistantMsg);

                if ("tool_calls".equals(finishReason)) {
                    // GPT wants to use one or more tools
                    List<Map<String, Object>> toolCalls = (List<Map<String, Object>>) assistantMsg.get("tool_calls");

                    for (Map<String, Object> tc : toolCalls) {
                        String              callId   = (String) tc.get("id");
                        Map<String, Object> fn       = (Map<String, Object>) tc.get("function");
                        String              toolName = (String) fn.get("name");
                        String              toolArgs = (String) fn.get("arguments");

                        String toolResult = executeTool(toolName, toolArgs);

                        // Collect the actual CarResponse objects so the frontend can render cards
                        if ("search_cars".equals(toolName)) {
                            try {
                                Map<String, Object>       parsed = mapper.readValue(toolResult, Map.class);
                                List<Map<String, Object>> list   = (List<Map<String, Object>>) parsed.get("cars");
                                if (list != null) {
                                    for (Map<String, Object> cs : list) {
                                        try {
                                            Long id = ((Number) cs.get("id")).longValue();
                                            foundCars.add(carService.getCarById(id));
                                        } catch (Exception ignored) {}
                                    }
                                }
                            } catch (Exception ignored) {}
                        }

                        // Append the tool result as a "tool" role message
                        Map<String, Object> toolMsg = new LinkedHashMap<>();
                        toolMsg.put("role",        "tool");
                        toolMsg.put("tool_call_id", callId);
                        toolMsg.put("name",         toolName);
                        toolMsg.put("content",      toolResult);
                        messages.add(toolMsg);
                    }
                    // Loop back — GPT will now write a reply using the tool results

                } else {
                    // GPT gave a final text answer — we're done
                    String content = (String) assistantMsg.get("content");
                    return new AgentResponse(content != null ? content.trim() : "", foundCars);
                }

            } catch (Exception e) {
                System.err.println("❌ CarFinderAgent round " + round + ": " + e.getMessage());
                return new AgentResponse("Something went wrong. Please try again.", List.of());
            }
        }

        return new AgentResponse("I had trouble processing that. Please try again.", foundCars);
    }

    private boolean contains(String field, String kw) {
        return field != null && field.toLowerCase().contains(kw);
    }

    // ── Response DTO ─────────────────────────────────────────────────────────

    public static class AgentResponse {
        public final String          message;
        public final List<CarResponse> cars;

        public AgentResponse(String message, List<CarResponse> cars) {
            this.message = message;
            this.cars    = cars;
        }
    }
}
