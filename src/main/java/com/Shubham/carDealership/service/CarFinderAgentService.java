package com.Shubham.carDealership.service;

import com.Shubham.carDealership.config.JwtUtil;
import com.Shubham.carDealership.dto.CarResponse;
import com.Shubham.carDealership.model.Car;
import com.Shubham.carDealership.model.ServiceAppointment;
import com.Shubham.carDealership.model.User;
import com.Shubham.carDealership.repository.CarRepository;
import com.Shubham.carDealership.repository.ServiceAppointmentRepository;
import com.Shubham.carDealership.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Agent 1 — Smart Car Finder
 *
 * Uses OpenAI function calling so GPT can:
 *   1. search_cars       — query live inventory
 *   2. get_car_details   — fetch full info on a specific car
 *   3. book_test_drive   — actually create a real appointment + send confirmation email
 *
 * The agentic loop: GPT picks a tool → we execute it against our real DB →
 * results go back to GPT → GPT writes a final human reply.
 */
@Service
public class CarFinderAgentService {

    @Value("${openai.api.key:}")
    private String apiKey;

    @Autowired private CarService carService;
    @Autowired private CarRepository carRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ServiceAppointmentRepository appointmentRepository;
    @Autowired private EmailService emailService;
    @Autowired private JwtUtil jwtUtil;

    private static final List<String> ACTIVE_STATUSES = List.of("SCHEDULED", "CONFIRMED");
    private final ObjectMapper mapper = new ObjectMapper();

    // ── System prompt ─────────────────────────────────────────────────────────

    private String buildSystemPrompt() {
        String today = LocalDateTime.now().format(DateTimeFormatter.ofPattern("EEEE, dd MMMM yyyy"));
        return "You are a smart car-finding and booking assistant for Shubham's Car Dealership in Auckland, NZ. " +
               "Today is " + today + ".\n\n" +

               "What you CAN do:\n" +
               "- Search our live inventory for cars matching what the customer describes.\n" +
               "- Get full details on any car.\n" +
               "- Book a real test drive appointment — use book_test_drive to actually create the booking.\n\n" +

               "Rules:\n" +
               "- Always call search_cars before describing cars — never invent vehicles.\n" +
               "- For test drive bookings: if the user hasn't specified a date/time, ask them before calling book_test_drive.\n" +
               "- Convert natural language dates to ISO format (e.g. 'tomorrow at 2pm' → '2026-06-30T14:00:00').\n" +
               "- If the user is not logged in, book_test_drive will tell you — then ask them to log in first.\n" +
               "- Only book TEST_DRIVE for DEALERSHIP cars, not private seller cars (the tool will tell you if wrong).\n" +
               "- Keep every reply under 150 words.\n" +
               "- Stick to car and dealership topics only.\n" +
               "- When a booking succeeds, confirm the date, time and car name clearly.";
    }

    // ── Tool definitions ──────────────────────────────────────────────────────

    private List<Map<String, Object>> buildTools() {

        // Tool 1: search_cars
        Map<String, Object> searchProps = new LinkedHashMap<>();
        searchProps.put("keyword",  Map.of("type", "string",  "description", "Free-text: make, model, year, keyword"));
        searchProps.put("make",     Map.of("type", "string",  "description", "Car brand, e.g. Toyota, BMW"));
        searchProps.put("bodyType", Map.of("type", "string",  "description", "Sedan | SUV | Hatchback | Ute | Wagon | Coupe | Convertible"));
        searchProps.put("fuel",     Map.of("type", "string",  "description", "Petrol | Diesel | Hybrid | Electric"));
        searchProps.put("maxPrice", Map.of("type", "number",  "description", "Max price in NZD"));
        searchProps.put("minYear",  Map.of("type", "integer", "description", "Minimum manufacture year"));

        Map<String, Object> searchFn = new LinkedHashMap<>();
        searchFn.put("name", "search_cars");
        searchFn.put("description", "Search available cars in live inventory. Always call this first when a customer describes what they want.");
        searchFn.put("parameters", Map.of("type", "object", "properties", searchProps, "required", List.of()));

        // Tool 2: get_car_details
        Map<String, Object> detailProps = Map.of("carId", Map.of("type", "integer", "description", "The car's numeric ID"));
        Map<String, Object> detailFn = new LinkedHashMap<>();
        detailFn.put("name", "get_car_details");
        detailFn.put("description", "Get full details (description, inspection status, seller) for a specific car by ID.");
        detailFn.put("parameters", Map.of("type", "object", "properties", detailProps, "required", List.of("carId")));

        // Tool 3: book_test_drive
        Map<String, Object> bookProps = new LinkedHashMap<>();
        bookProps.put("carId",           Map.of("type", "integer", "description", "ID of the car to test drive"));
        bookProps.put("appointmentDate", Map.of("type", "string",  "description", "ISO datetime e.g. 2026-07-01T14:00:00"));
        bookProps.put("notes",           Map.of("type", "string",  "description", "Optional notes from the customer"));

        Map<String, Object> bookFn = new LinkedHashMap<>();
        bookFn.put("name", "book_test_drive");
        bookFn.put("description", "Book a real test drive appointment. Creates the booking in the database and sends a confirmation email. Only works for logged-in users.");
        bookFn.put("parameters", Map.of("type", "object", "properties", bookProps, "required", List.of("carId", "appointmentDate")));

        return List.of(
            Map.of("type", "function", "function", searchFn),
            Map.of("type", "function", "function", detailFn),
            Map.of("type", "function", "function", bookFn)
        );
    }

    // ── Tool execution ────────────────────────────────────────────────────────

    private String executeTool(String toolName, String argsJson, String jwtToken) {
        try {
            Map<String, Object> args = mapper.readValue(argsJson, Map.class);

            // ── search_cars ──────────────────────────────────────────────────
            if ("search_cars".equals(toolName)) {
                String keyword  = (String) args.getOrDefault("keyword",  null);
                String make     = (String) args.getOrDefault("make",     null);
                String bodyType = (String) args.getOrDefault("bodyType", null);
                String fuel     = (String) args.getOrDefault("fuel",     null);
                Double maxPrice = args.get("maxPrice") != null ? ((Number) args.get("maxPrice")).doubleValue() : null;
                Integer minYear = args.get("minYear")  != null ? ((Number) args.get("minYear")).intValue()    : null;

                List<CarResponse> cars = carService.searchCars(make, bodyType, fuel, maxPrice, null);

                if (keyword != null && !keyword.isBlank()) {
                    String kw = keyword.toLowerCase();
                    cars = cars.stream().filter(c ->
                        contains(c.getMake(), kw) || contains(c.getModel(), kw) ||
                        contains(c.getDescription(), kw) || String.valueOf(c.getYear()).contains(kw)
                    ).collect(Collectors.toList());
                }
                if (minYear != null) {
                    int yr = minYear;
                    cars = cars.stream().filter(c -> c.getYear() != null && c.getYear() >= yr).collect(Collectors.toList());
                }
                if (cars.size() > 5) cars = cars.subList(0, 5);

                if (cars.isEmpty()) return mapper.writeValueAsString(Map.of("found", 0, "message", "No cars matched."));

                List<Map<String, Object>> summaries = new ArrayList<>();
                for (CarResponse c : cars) {
                    Map<String, Object> s = new LinkedHashMap<>();
                    s.put("id", c.getId()); s.put("make", c.getMake()); s.put("model", c.getModel());
                    s.put("year", c.getYear()); s.put("price", c.getPrice());
                    s.put("mileage", c.getMileage() + " km"); s.put("fuel", c.getFuel());
                    s.put("transmission", c.getTransmission()); s.put("bodyType", c.getBodyType());
                    s.put("source", c.getCarSource()); s.put("inspected", "PASSED".equals(c.getInspectionStatus()));
                    summaries.add(s);
                }
                return mapper.writeValueAsString(Map.of("found", cars.size(), "cars", summaries));

            // ── get_car_details ──────────────────────────────────────────────
            } else if ("get_car_details".equals(toolName)) {
                Long carId = ((Number) args.get("carId")).longValue();
                CarResponse c = carService.getCarById(carId);
                Map<String, Object> s = new LinkedHashMap<>();
                s.put("id", c.getId()); s.put("make", c.getMake()); s.put("model", c.getModel());
                s.put("year", c.getYear()); s.put("price", c.getPrice());
                s.put("mileage", c.getMileage() + " km"); s.put("fuel", c.getFuel());
                s.put("transmission", c.getTransmission()); s.put("bodyType", c.getBodyType());
                s.put("description", c.getDescription()); s.put("source", c.getCarSource());
                s.put("inspected", "PASSED".equals(c.getInspectionStatus()));
                return mapper.writeValueAsString(s);

            // ── book_test_drive ──────────────────────────────────────────────
            } else if ("book_test_drive".equals(toolName)) {
                return executeBookTestDrive(args, jwtToken);
            }

        } catch (Exception e) {
            System.err.println("❌ Agent tool error [" + toolName + "]: " + e.getMessage());
            return "{\"error\":\"" + e.getMessage() + "\"}";
        }
        return "{\"error\":\"unknown tool\"}";
    }

    private String executeBookTestDrive(Map<String, Object> args, String jwtToken) throws Exception {
        // Must be logged in
        if (jwtToken == null || jwtToken.isBlank() || !jwtUtil.validateToken(jwtToken)) {
            return mapper.writeValueAsString(Map.of(
                "success", false,
                "reason",  "not_logged_in",
                "message", "The customer is not logged in. Tell them they need to log in before booking a test drive."
            ));
        }

        Long userId = jwtUtil.extractUserId(jwtToken);
        User user   = userRepository.findById(userId).orElse(null);
        if (user == null) return mapper.writeValueAsString(Map.of("success", false, "message", "User not found."));

        Long carId = ((Number) args.get("carId")).longValue();
        Car  car   = carRepository.findById(carId).orElse(null);
        if (car == null) return mapper.writeValueAsString(Map.of("success", false, "message", "Car not found."));

        // Dealership only
        if ("MARKETPLACE".equals(car.getCarSource())) {
            return mapper.writeValueAsString(Map.of(
                "success", false,
                "message", "This is a private seller car — test drives must be arranged directly with the seller via the messaging feature."
            ));
        }

        LocalDateTime appointmentDate = LocalDateTime.parse(args.get("appointmentDate").toString());

        // No duplicate bookings
        List<ServiceAppointment> existing = appointmentRepository
            .findByUserIdAndCarIdAndStatusIn(userId, carId, ACTIVE_STATUSES);
        if (!existing.isEmpty()) {
            return mapper.writeValueAsString(Map.of(
                "success", false,
                "message", "This customer already has an active booking for this car on " +
                           existing.get(0).getAppointmentDate() + ". They can reschedule it from their dashboard."
            ));
        }

        // Slot availability
        boolean slotTaken = appointmentRepository.findByCarIdAndStatusIn(carId, ACTIVE_STATUSES)
            .stream().anyMatch(a -> a.getAppointmentDate().equals(appointmentDate));
        if (slotTaken) {
            return mapper.writeValueAsString(Map.of(
                "success", false,
                "message", "That time slot is already taken. Ask the customer to pick a different time."
            ));
        }

        // Create the appointment
        ServiceAppointment appt = new ServiceAppointment();
        appt.setUserId(userId);
        appt.setCarId(carId);
        appt.setServiceType("TEST_DRIVE");
        appt.setAppointmentDate(appointmentDate);
        appt.setStatus("SCHEDULED");
        appt.setCreatedAt(LocalDateTime.now());
        if (args.containsKey("notes") && args.get("notes") != null) {
            appt.setNotes(args.get("notes").toString());
        }
        appointmentRepository.save(appt);

        // Send confirmation email
        String carName = car.getYear() + " " + car.getMake() + " " + car.getModel();
        try {
            emailService.sendTestDriveBooked(user.getEmail(), user.getUsername(), carName, appointmentDate);
        } catch (Exception e) {
            System.err.println("⚠️ Agent email send failed (booking still created): " + e.getMessage());
        }

        return mapper.writeValueAsString(Map.of(
            "success",   true,
            "carName",   carName,
            "date",      appointmentDate.format(DateTimeFormatter.ofPattern("EEEE, dd MMMM yyyy 'at' h:mm a")),
            "userEmail", user.getEmail(),
            "message",   "Booking confirmed in the database and confirmation email sent to " + user.getEmail()
        ));
    }

    // ── Main agent loop ───────────────────────────────────────────────────────

    public AgentResponse chat(String userMessage, List<Map<String, Object>> history, String jwtToken) {
        if (apiKey == null || apiKey.isBlank()) {
            return new AgentResponse("AI agent is not configured — please add an OpenAI API key.", List.of());
        }

        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", buildSystemPrompt()));
        if (history != null) messages.addAll(history);
        messages.add(Map.of("role", "user", "content", userMessage));

        List<Map<String, Object>> tools     = buildTools();
        List<CarResponse>         foundCars = new ArrayList<>();

        for (int round = 0; round < 5; round++) {  // up to 5 tool-call rounds
            try {
                Map<String, Object> requestBody = new LinkedHashMap<>();
                requestBody.put("model",       "gpt-4o-mini");
                requestBody.put("messages",    messages);
                requestBody.put("tools",       tools);
                requestBody.put("tool_choice", "auto");
                requestBody.put("max_tokens",  500);
                requestBody.put("temperature", 0.4);

                String reqJson = mapper.writeValueAsString(requestBody);
                URL    url     = new URL("https://api.openai.com/v1/chat/completions");

                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type",  "application/json");
                conn.setRequestProperty("Authorization", "Bearer " + apiKey);
                conn.setDoOutput(true);
                conn.setConnectTimeout(15_000);
                conn.setReadTimeout(30_000);
                try (OutputStream os = conn.getOutputStream()) { os.write(reqJson.getBytes("utf-8")); }

                if (conn.getResponseCode() != 200) {
                    return new AgentResponse("I'm having trouble reaching the AI. Please try again.", List.of());
                }

                BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "utf-8"));
                StringBuilder  sb = new StringBuilder(); String ln;
                while ((ln = br.readLine()) != null) sb.append(ln);
                br.close();

                Map<String, Object>       resp         = mapper.readValue(sb.toString(), Map.class);
                List<Map<String, Object>> choices      = (List<Map<String, Object>>) resp.get("choices");
                Map<String, Object>       assistantMsg = (Map<String, Object>) choices.get(0).get("message");
                String                    finishReason = (String) choices.get(0).get("finish_reason");

                messages.add(assistantMsg);

                if ("tool_calls".equals(finishReason)) {
                    List<Map<String, Object>> toolCalls = (List<Map<String, Object>>) assistantMsg.get("tool_calls");

                    for (Map<String, Object> tc : toolCalls) {
                        String              callId   = (String) tc.get("id");
                        Map<String, Object> fn       = (Map<String, Object>) tc.get("function");
                        String              toolName = (String) fn.get("name");
                        String              toolArgs = (String) fn.get("arguments");

                        String toolResult = executeTool(toolName, toolArgs, jwtToken);

                        // Collect car objects for frontend card rendering
                        if ("search_cars".equals(toolName)) {
                            try {
                                Map<String, Object>       parsed = mapper.readValue(toolResult, Map.class);
                                List<Map<String, Object>> list   = (List<Map<String, Object>>) parsed.get("cars");
                                if (list != null) {
                                    for (Map<String, Object> cs : list) {
                                        try { foundCars.add(carService.getCarById(((Number) cs.get("id")).longValue())); }
                                        catch (Exception ignored) {}
                                    }
                                }
                            } catch (Exception ignored) {}
                        }

                        Map<String, Object> toolMsg = new LinkedHashMap<>();
                        toolMsg.put("role",        "tool");
                        toolMsg.put("tool_call_id", callId);
                        toolMsg.put("name",         toolName);
                        toolMsg.put("content",      toolResult);
                        messages.add(toolMsg);
                    }

                } else {
                    String content = (String) assistantMsg.get("content");
                    return new AgentResponse(content != null ? content.trim() : "", foundCars);
                }

            } catch (Exception e) {
                System.err.println("❌ CarFinderAgent round " + round + ": " + e.getMessage());
                return new AgentResponse("Something went wrong. Please try again.", List.of());
            }
        }

        return new AgentResponse("I had trouble completing that. Please try again.", foundCars);
    }

    private boolean contains(String field, String kw) {
        return field != null && field.toLowerCase().contains(kw);
    }

    // ── Response DTO ─────────────────────────────────────────────────────────

    public static class AgentResponse {
        public final String            message;
        public final List<CarResponse> cars;
        public AgentResponse(String message, List<CarResponse> cars) {
            this.message = message;
            this.cars    = cars;
        }
    }
}
