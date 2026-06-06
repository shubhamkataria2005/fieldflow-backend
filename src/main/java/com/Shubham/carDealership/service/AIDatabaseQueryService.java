package com.Shubham.carDealership.service;

import com.Shubham.carDealership.model.Car;
import com.Shubham.carDealership.repository.CarRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.sql.*;
import java.util.*;
import javax.sql.DataSource;

@Service
public class AIDatabaseQueryService {

    @Autowired
    private CarRepository carRepository;

    @Autowired
    private DataSource dataSource;

    // This will be set by OpenAIService to avoid circular dependency
    private OpenAIService openAIService;

    public void setOpenAIService(OpenAIService service) {
        this.openAIService = service;
    }

    public String handleNaturalLanguageQuery(String userMessage) {
        if (openAIService == null) {
            return "AI service not initialized yet. Please try again.";
        }

        return callOpenAIForQuery(userMessage);
    }

    private String callOpenAIForQuery(String userMessage) {
        try {
            String schema = getDatabaseSchema();
            String prompt = buildAIPrompt(userMessage, schema);

            System.out.println("🤖 Asking OpenAI to understand: " + userMessage);

            String sqlQuery = openAIService.generateCompletion(prompt);

            if (sqlQuery == null || sqlQuery.isEmpty()) {
                return "I couldn't understand that question. Could you rephrase it?";
            }

            System.out.println("📝 OpenAI generated SQL: " + sqlQuery);

            List<Map<String, Object>> results = executeSQL(sqlQuery);

            return formatResults(results, userMessage);

        } catch (Exception e) {
            System.err.println("❌ AI Query failed: " + e.getMessage());
            return "I'm having trouble understanding. Could you ask differently?";
        }
    }

    private String buildAIPrompt(String userMessage, String schema) {
        return """
            You are a SQL expert for a car dealership database.
            
            DATABASE SCHEMA:
            """ + schema + """
            
            RULES:
            - Only query cars with status = 'AVAILABLE'
            - Car source: 'DEALERSHIP' (company cars) or 'MARKETPLACE' (private sellers)
            - Return ONLY the SQL query, no explanation
            - Use LIMIT 5 for listing queries
            
            EXAMPLES:
            Question: "how many bmw cars do you have"
            SQL: SELECT COUNT(*) FROM cars WHERE make = 'BMW' AND status = 'AVAILABLE'
            
            Question: "show me cheap cars under 30000"
            SQL: SELECT make, model, year, price, mileage FROM cars WHERE price < 30000 AND status = 'AVAILABLE' LIMIT 5
            
            Question: "what's the most expensive car"
            SQL: SELECT make, model, year, price FROM cars WHERE status = 'AVAILABLE' ORDER BY price DESC LIMIT 1
            
            Question: "any electric cars available"
            SQL: SELECT make, model, year, price FROM cars WHERE fuel = 'Electric' AND status = 'AVAILABLE' LIMIT 5
            
            Question: """ + userMessage + """
            
            SQL:""";
    }

    private String getDatabaseSchema() {
        // Get sample data to help AI understand
        List<Car> sampleCars = carRepository.findAll().stream().limit(3).toList();
        StringBuilder sampleData = new StringBuilder();
        for (Car car : sampleCars) {
            sampleData.append(String.format("  %d %s %s, $%.0f, %d miles, %s, %s\n",
                    car.getYear(), car.getMake(), car.getModel(),
                    car.getPrice(), car.getMileage(), car.getFuel(), car.getCarSource()));
        }

        return """
            Table: cars
            Columns:
              - make (VARCHAR) - brand: BMW, Toyota, Honda, Tesla, Mercedes, Audi, Ford
              - model (VARCHAR) - model name
              - year (INTEGER) - manufacturing year
              - price (DECIMAL) - price in dollars
              - mileage (INTEGER) - miles driven
              - fuel (VARCHAR) - Petrol, Diesel, Electric, Hybrid
              - transmission (VARCHAR) - Automatic, Manual
              - body_type (VARCHAR) - SUV, Sedan, Truck, Coupe
              - status (VARCHAR) - AVAILABLE, SOLD
              - car_source (VARCHAR) - DEALERSHIP, MARKETPLACE
            
            Sample data:
            """ + sampleData.toString();
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
        if (results.isEmpty()) {
            return "No cars found matching your criteria. Try asking differently!";
        }

        // Check if it's a COUNT query
        if (results.size() == 1 && results.get(0).containsKey("count")) {
            long count = ((Number) results.get(0).get("count")).longValue();
            if (count == 0) {
                return "Sorry, no cars match your request.";
            }
            return String.format("🎯 Found %d car(s) matching your request.", count);
        }

        // Format listing results
        StringBuilder response = new StringBuilder();
        response.append("🎯 Here's what I found:\n\n");

        for (Map<String, Object> row : results) {
            if (row.containsKey("make") && row.containsKey("model")) {
                response.append("• ");
                if (row.containsKey("year")) response.append(row.get("year")).append(" ");
                response.append(row.get("make")).append(" ").append(row.get("model"));
                if (row.containsKey("price")) response.append(" - $").append(row.get("price"));
                if (row.containsKey("mileage")) response.append(" (").append(row.get("mileage")).append(" miles)");
                response.append("\n");
            }
        }

        return response.toString();
    }

    public List<Car> getAllAvailableCars() {
        return carRepository.findByStatus("AVAILABLE");
    }
}