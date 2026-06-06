package com.Shubham.carDealership.service;

import com.Shubham.carDealership.model.Car;
import com.Shubham.carDealership.repository.CarRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;
import javax.sql.DataSource;

@Service
public class AIDatabaseQueryService {

    @Autowired
    private CarRepository carRepository;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private OpenAIService openAIService;

    public String handleNaturalLanguageQuery(String userMessage) {
        // Use OpenAI to convert natural language to SQL and response
        return callOpenAIForQuery(userMessage);
    }

    private String callOpenAIForQuery(String userMessage) {
        try {
            // Step 1: Get database schema
            String schema = getDatabaseSchema();

            // Step 2: Ask OpenAI to generate SQL and response format
            String prompt = buildAIPrompt(userMessage, schema);

            String aiResponse = openAIService.generateCompletion(prompt);

            if (aiResponse == null || aiResponse.isEmpty()) {
                return getFallbackResponse(userMessage);
            }

            // Step 3: Parse AI response (expects JSON with SQL and format)
            return processAIResponse(aiResponse, userMessage);

        } catch (Exception e) {
            System.err.println("❌ AI Query failed: " + e.getMessage());
            return getFallbackResponse(userMessage);
        }
    }

    private String buildAIPrompt(String userMessage, String schema) {
        return """
            You are a car dealership database assistant. Your job is to help users get information about cars.
            
            DATABASE SCHEMA:
            """ + schema + """
            
            RULES:
            1. Always use status = 'AVAILABLE' for available cars
            2. Car source can be 'DEALERSHIP' or 'MARKETPLACE'
            3. Convert user questions to SQL queries
            4. Return response in JSON format only, no other text
            
            USER QUESTION: """ + userMessage + """
            
            Respond in this EXACT JSON format:
            {
                "sql": "SELECT ...",
                "response_template": "We have {count} {brand} cars available",
                "needs_execution": true
            }
            
            If the question is not about cars, return:
            {
                "sql": null,
                "response_template": "I can only help with car-related questions",
                "needs_execution": false
            }
            
            For counting questions, use COUNT(*).
            For listing questions, use SELECT with LIMIT 5.
            For price questions, use MIN, MAX, or AVG.
            
            Return ONLY the JSON, no other text.
            """;
    }

    private String processAIResponse(String aiResponse, String originalQuery) {
        try {
            // Parse JSON response from OpenAI
            com.fasterxml.jackson.databind.ObjectMapper mapper =
                    new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode json = mapper.readTree(aiResponse);

            String sql = json.has("sql") && !json.get("sql").isNull() ?
                    json.get("sql").asText() : null;
            String template = json.has("response_template") ?
                    json.get("response_template").asText() : "";
            boolean needsExecution = json.has("needs_execution") &&
                    json.get("needs_execution").asBoolean();

            if (!needsExecution || sql == null) {
                return template.isEmpty() ?
                        "I can only help with car-related questions." : template;
            }

            // Execute the SQL query
            List<Map<String, Object>> results = executeSQL(sql);

            // Format the response using the template
            return formatResponse(template, results, originalQuery);

        } catch (Exception e) {
            System.err.println("❌ Failed to process AI response: " + e.getMessage());
            return getFallbackResponse(originalQuery);
        }
    }

    private String getDatabaseSchema() {
        StringBuilder schema = new StringBuilder();
        schema.append("Table: cars\n");
        schema.append("Columns:\n");
        schema.append("  - id (BIGINT, primary key)\n");
        schema.append("  - make (VARCHAR, car brand like 'BMW', 'Toyota', 'Honda', 'Tesla')\n");
        schema.append("  - model (VARCHAR, car model like 'X5', 'Camry', 'Civic')\n");
        schema.append("  - year (INTEGER, manufacturing year)\n");
        schema.append("  - price (DECIMAL, price in dollars)\n");
        schema.append("  - mileage (INTEGER, miles driven)\n");
        schema.append("  - fuel (VARCHAR, 'Petrol', 'Diesel', 'Electric', 'Hybrid')\n");
        schema.append("  - transmission (VARCHAR, 'Automatic' or 'Manual')\n");
        schema.append("  - body_type (VARCHAR, 'SUV', 'Sedan', 'Truck', 'Coupe', 'Hatchback')\n");
        schema.append("  - status (VARCHAR, 'AVAILABLE', 'SOLD', 'RESERVED')\n");
        schema.append("  - car_source (VARCHAR, 'DEALERSHIP' or 'MARKETPLACE')\n");
        schema.append("Sample data (first 5 cars):\n");

        List<Car> sampleCars = carRepository.findAll().stream().limit(5).collect(Collectors.toList());
        for (Car car : sampleCars) {
            schema.append(String.format("  - %d %s %s, $%.0f, %d miles, %s, %s, %s, %s\n",
                    car.getYear(), car.getMake(), car.getModel(),
                    car.getPrice(), car.getMileage(), car.getFuel(),
                    car.getTransmission(), car.getStatus(), car.getCarSource()));
        }

        return schema.toString();
    }

    private List<Map<String, Object>> executeSQL(String sql) {
        List<Map<String, Object>> results = new ArrayList<>();

        // Try using JPA for common queries (faster)
        String lowerSql = sql.toLowerCase();

        // Handle COUNT queries with brand filter
        if (lowerSql.contains("count(*)") && lowerSql.contains("make")) {
            String brand = extractBrandFromSQL(sql);
            if (brand != null) {
                long count = carRepository.findByStatus("AVAILABLE").stream()
                        .filter(c -> c.getMake().equalsIgnoreCase(brand))
                        .count();
                Map<String, Object> result = new HashMap<>();
                result.put("count", count);
                result.put("brand", brand);
                results.add(result);
                return results;
            }
        }

        // Handle simple SELECT queries
        if (lowerSql.contains("select") && (lowerSql.contains("where") || lowerSql.contains("limit"))) {
            // For listing queries, use JPA to get cars
            if (lowerSql.contains("make")) {
                String brand = extractBrandFromSQL(sql);
                if (brand != null) {
                    List<Car> cars = carRepository.findByStatus("AVAILABLE").stream()
                            .filter(c -> c.getMake().equalsIgnoreCase(brand))
                            .limit(5)
                            .collect(Collectors.toList());
                    for (Car car : cars) {
                        Map<String, Object> result = new HashMap<>();
                        result.put("make", car.getMake());
                        result.put("model", car.getModel());
                        result.put("year", car.getYear());
                        result.put("price", car.getPrice());
                        result.put("mileage", car.getMileage());
                        results.add(result);
                    }
                    return results;
                }
            }

            // Handle price range queries
            if (lowerSql.contains("price") && lowerSql.contains("<")) {
                // Extract max price from SQL
                java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("price\\s*<\\s*(\\d+)");
                java.util.regex.Matcher matcher = pattern.matcher(lowerSql);
                if (matcher.find()) {
                    double maxPrice = Double.parseDouble(matcher.group(1));
                    List<Car> cars = carRepository.findByStatus("AVAILABLE").stream()
                            .filter(c -> c.getPrice().doubleValue() <= maxPrice)
                            .limit(5)
                            .collect(Collectors.toList());
                    for (Car car : cars) {
                        Map<String, Object> result = new HashMap<>();
                        result.put("make", car.getMake());
                        result.put("model", car.getModel());
                        result.put("year", car.getYear());
                        result.put("price", car.getPrice());
                        result.put("mileage", car.getMileage());
                        results.add(result);
                    }
                    return results;
                }
            }
        }

        // Fallback to native SQL for complex queries
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            ResultSetMetaData metaData = rs.getMetaData();
            int columnCount = metaData.getColumnCount();

            while (rs.next()) {
                Map<String, Object> row = new HashMap<>();
                for (int i = 1; i <= columnCount; i++) {
                    String columnName = metaData.getColumnLabel(i);
                    Object value = rs.getObject(i);
                    row.put(columnName, value);
                }
                results.add(row);
            }
        } catch (SQLException e) {
            System.err.println("❌ SQL Execution failed: " + e.getMessage());
        }

        return results;
    }

    private String extractBrandFromSQL(String sql) {
        String lower = sql.toLowerCase();
        String[] brands = {"bmw", "toyota", "honda", "tesla", "mercedes", "audi", "ford",
                "hyundai", "kia", "mazda", "subaru", "nissan", "volkswagen", "porsche"};
        for (String brand : brands) {
            if (lower.contains("make = '" + brand + "'") ||
                    lower.contains("make = '" + brand.toUpperCase() + "'") ||
                    lower.contains("make like '%" + brand + "%'")) {
                return brand.substring(0, 1).toUpperCase() + brand.substring(1);
            }
        }
        return null;
    }

    private String formatResponse(String template, List<Map<String, Object>> results, String originalQuery) {
        if (results.isEmpty()) {
            return "No cars found matching your criteria. Try adjusting your search or ask about different brands!";
        }

        // Handle count queries
        if (template.contains("{count}") && results.size() > 0 && results.get(0).containsKey("count")) {
            long count = ((Number) results.get(0).get("count")).longValue();
            String brand = results.get(0).containsKey("brand") ?
                    results.get(0).get("brand").toString() : "";

            if (count == 0) {
                return "Sorry, no " + brand + " cars are available right now. Would you like to see other brands?";
            }

            String response = template.replace("{count}", String.valueOf(count))
                    .replace("{brand}", brand);

            // Add a helpful follow-up
            if (count > 0 && !originalQuery.toLowerCase().contains("list")) {
                response += " Would you like me to list them?";
            }

            return response;
        }

        // Handle listing queries
        if (results.size() > 0) {
            StringBuilder response = new StringBuilder();
            response.append("Here's what I found:\n\n");

            int limit = Math.min(results.size(), 5);
            for (int i = 0; i < limit; i++) {
                Map<String, Object> car = results.get(i);
                response.append("• ");
                if (car.containsKey("year")) response.append(car.get("year")).append(" ");
                if (car.containsKey("make")) response.append(car.get("make")).append(" ");
                if (car.containsKey("model")) response.append(car.get("model"));
                if (car.containsKey("price")) response.append(" - $").append(car.get("price"));
                if (car.containsKey("mileage")) response.append(" (").append(car.get("mileage")).append(" miles)");
                response.append("\n");
            }

            if (results.size() > 5) {
                response.append("\nAnd ").append(results.size() - 5).append(" more results.");
            }

            response.append("\n\nWould you like more details about any of these?");
            return response.toString();
        }

        return template.isEmpty() ?
                String.format("Found %d results. Ask me for more details!", results.size()) : template;
    }

    private String getFallbackResponse(String userMessage) {
        String lower = userMessage.toLowerCase();

        if (lower.contains("hello") || lower.contains("hi") || lower.contains("hey")) {
            return "Hello! 👋 Welcome to Shubham's Car Dealership! Ask me anything about our cars - prices, availability, brands, or recommendations. How can I help you today?";
        }

        if (lower.contains("test drive")) {
            return "🚗 To book a test drive:\n1. Log in to your account\n2. Go to Dashboard → Service Center\n3. Select 'Book Test Drive'\n4. Choose the car and date/time\n\nOur team will confirm within 24 hours!";
        }

        if (lower.contains("trade")) {
            return "🔄 To trade in your car:\n1. Go to Dashboard → Trade-In section\n2. Enter your car details (rego, make, model, year, mileage, condition)\n3. Get instant ML-powered valuation\n4. Submit for admin approval\n\nOnce approved, use the value towards your purchase!";
        }

        if (lower.contains("finance") || lower.contains("loan") || lower.contains("payment")) {
            return "🏦 Financing Options:\n• Use our Finance Calculator in Dashboard\n• Multiple bank partners for competitive rates\n• Flexible terms: 12-84 months\n• Special offers for first-time buyers\n\nCalculate your monthly payments in the Finance section!";
        }

        if (lower.contains("sell") || lower.contains("list")) {
            return "📝 To sell your car on Marketplace:\n1. Log in to your account\n2. Go to Dashboard → List on Marketplace\n3. Enter car details and price\n4. Upload photos\n5. Submit for review\n\nIt's completely FREE!";
        }

        if (lower.contains("service") || lower.contains("maintenance")) {
            return "🔧 Service Center Services:\n• Routine Maintenance\n• Repairs & Diagnostics\n• Car Inspections\n• Test Drives\n\nBook an appointment through Dashboard → Service Center!";
        }

        if (lower.contains("where") && (lower.contains("located") || lower.contains("location"))) {
            return "📍 Shubham's Car Dealership\n123 Auckland City Centre\nAuckland CBD, New Zealand\n\nWe're in the heart of Auckland!";
        }

        if (lower.contains("hour") || lower.contains("open")) {
            return "🕐 Dealership Hours:\nMonday-Friday: 9:00 AM - 6:00 PM\nSaturday: 10:00 AM - 4:00 PM\nSunday: Closed";
        }

        return "I'm here to help! 🚗 Ask me about:\n• Car inventory and availability\n• Prices and budget recommendations\n• Specific brands (BMW, Toyota, Tesla, etc.)\n• Test drives, trade-ins, and financing\n\nWhat would you like to know?";
    }

    public List<Car> getAllAvailableCars() {
        return carRepository.findByStatus("AVAILABLE");
    }
}