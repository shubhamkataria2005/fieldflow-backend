package com.Shubham.carDealership.service;

import com.Shubham.carDealership.model.Car;
import com.Shubham.carDealership.repository.CarRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
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
    @Lazy  // This breaks the circular dependency!
    private OpenAIService openAIService;

    public String handleNaturalLanguageQuery(String userMessage) {
        // Use TRUE AI to understand the query
        return callOpenAIForQuery(userMessage);
    }

    private String callOpenAIForQuery(String userMessage) {
        try {
            // Get database schema
            String schema = getDatabaseSchema();

            // Build prompt for OpenAI
            String prompt = buildAIPrompt(userMessage, schema);

            // Call OpenAI
            String aiResponse = openAIService.generateCompletion(prompt);

            if (aiResponse == null || aiResponse.isEmpty()) {
                return getFallbackResponse(userMessage);
            }

            // Parse and execute SQL
            return processAIResponse(aiResponse, userMessage);

        } catch (Exception e) {
            System.err.println("❌ AI Query failed: " + e.getMessage());
            return getFallbackResponse(userMessage);
        }
    }

    private String buildAIPrompt(String userMessage, String schema) {
        return """
            You are a car dealership database assistant. Convert user questions to SQL.
            
            DATABASE SCHEMA:
            """ + schema + """
            
            RULES:
            - Only query cars with status = 'AVAILABLE'
            - car_source can be 'DEALERSHIP' or 'MARKETPLACE'
            
            USER QUESTION: """ + userMessage + """
            
            Return ONLY valid SQL query, no explanation.
            Example: "how many BMW cars" -> SELECT COUNT(*) FROM cars WHERE make = 'BMW' AND status = 'AVAILABLE'
            Example: "cheap cars under 30000" -> SELECT make, model, year, price FROM cars WHERE price < 30000 AND status = 'AVAILABLE' LIMIT 5
            
            SQL:""";
    }

    private String processAIResponse(String sqlQuery, String originalQuery) {
        try {
            // Execute the SQL query
            List<Map<String, Object>> results = executeSQL(sqlQuery);

            if (results.isEmpty()) {
                return "No cars found matching your criteria.";
            }

            // Format results naturally
            return formatResults(results, originalQuery);

        } catch (Exception e) {
            System.err.println("❌ SQL Execution failed: " + e.getMessage());
            return getFallbackResponse(originalQuery);
        }
    }

    private List<Map<String, Object>> executeSQL(String sql) {
        List<Map<String, Object>> results = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            ResultSetMetaData metaData = rs.getMetaData();
            int columnCount = metaData.getColumnCount();

            while (rs.next()) {
                Map<String, Object> row = new HashMap<>();
                for (int i = 1; i <= columnCount; i++) {
                    row.put(metaData.getColumnLabel(i), rs.getObject(i));
                }
                results.add(row);
            }
        } catch (SQLException e) {
            System.err.println("SQL Error: " + e.getMessage());
        }

        return results;
    }

    private String formatResults(List<Map<String, Object>> results, String originalQuery) {
        // Check if it's a count query
        if (results.size() == 1 && results.get(0).containsKey("count")) {
            long count = ((Number) results.get(0).get("count")).longValue();
            return "🎯 " + (count == 0 ? "No" : count) + " car(s) found matching your request.";
        }

        // Format listing results
        StringBuilder response = new StringBuilder();
        response.append("🎯 Here's what I found:\n\n");

        for (Map<String, Object> row : results) {
            if (row.containsKey("make") && row.containsKey("model")) {
                response.append("• ").append(row.get("year")).append(" ")
                        .append(row.get("make")).append(" ").append(row.get("model"));
                if (row.containsKey("price")) {
                    response.append(" - $").append(row.get("price"));
                }
                response.append("\n");
            }
        }

        return response.toString();
    }

    private String getDatabaseSchema() {
        return """
            Table: cars
            Columns:
              - id (BIGINT)
              - make (VARCHAR) - car brand (BMW, Toyota, Honda, Tesla, etc.)
              - model (VARCHAR) - car model
              - year (INTEGER)
              - price (DECIMAL)
              - mileage (INTEGER)
              - fuel (VARCHAR) - Petrol, Diesel, Electric, Hybrid
              - transmission (VARCHAR) - Automatic, Manual
              - body_type (VARCHAR) - SUV, Sedan, Truck, Coupe
              - status (VARCHAR) - AVAILABLE, SOLD
              - car_source (VARCHAR) - DEALERSHIP, MARKETPLACE
            """;
    }

    private String getFallbackResponse(String userMessage) {
        return "I'm here to help! Ask me about car inventory, prices, or specific brands. 🚗";
    }

    public List<Car> getAllAvailableCars() {
        return carRepository.findByStatus("AVAILABLE");
    }
}