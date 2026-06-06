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

    // REMOVED: OpenAIService dependency - no longer needed
    // We'll use pattern matching instead of AI for now

    public String handleNaturalLanguageQuery(String userMessage) {
        // Use pattern matching instead of AI (no circular dependency)
        return handleWithPatternMatching(userMessage);
    }

    private String handleWithPatternMatching(String userMessage) {
        String lower = userMessage.toLowerCase();

        // Count queries
        if (lower.contains("how many") || lower.contains("count") || lower.contains("total")) {
            return handleCountQuery(lower);
        }

        // Brand queries
        String brand = extractBrand(lower);
        if (brand != null) {
            return handleBrandQuery(brand, lower);
        }

        // Price/budget queries
        Integer budget = extractBudget(lower);
        if (budget != null) {
            return handleBudgetQuery(budget, lower);
        }

        // Price range
        if (lower.contains("price range") || (lower.contains("price") && lower.contains("range"))) {
            return getPriceRange();
        }

        // Cheapest car
        if (lower.contains("cheapest") || lower.contains("least expensive")) {
            return getCheapestCar();
        }

        // Most expensive
        if (lower.contains("expensive") || lower.contains("most expensive")) {
            return getMostExpensiveCar();
        }

        // Vehicle type
        String vehicleType = extractVehicleType(lower);
        if (vehicleType != null) {
            return getCarsByType(vehicleType);
        }

        // Fuel type
        String fuelType = extractFuelType(lower);
        if (fuelType != null) {
            return getCarsByFuelType(fuelType);
        }

        // Availability
        if (lower.contains("available") || lower.contains("any car")) {
            return getAvailabilityInfo();
        }

        return null; // Let OpenAIService handle it
    }

    private String handleCountQuery(String query) {
        List<Car> allCars = getAllAvailableCars();
        String brand = extractBrand(query);

        if (brand != null) {
            long count = allCars.stream()
                    .filter(c -> c.getMake().equalsIgnoreCase(brand))
                    .count();
            if (count == 0) {
                return "Sorry, no " + brand + " cars are available right now.";
            }
            return "We have " + count + " " + brand + " car(s) available.";
        }

        long dealershipCount = allCars.stream()
                .filter(c -> "DEALERSHIP".equals(c.getCarSource()))
                .count();
        long marketplaceCount = allCars.stream()
                .filter(c -> "MARKETPLACE".equals(c.getCarSource()))
                .count();

        if (query.contains("dealership")) {
            return "🏢 We have " + dealershipCount + " car(s) in our dealership inventory.";
        } else if (query.contains("marketplace")) {
            return "🛒 There are " + marketplaceCount + " car(s) listed on our marketplace.";
        } else {
            return "🚗 We have " + allCars.size() + " car(s) total (" +
                    dealershipCount + " from dealership, " + marketplaceCount + " from marketplace).";
        }
    }

    private String handleBrandQuery(String brand, String query) {
        List<Car> brandCars = getAllAvailableCars().stream()
                .filter(c -> c.getMake().equalsIgnoreCase(brand))
                .collect(Collectors.toList());

        if (brandCars.isEmpty()) {
            return "Sorry, no " + brand + " cars are available right now.";
        }

        if (query.contains("price") || query.contains("cost")) {
            BigDecimal min = brandCars.stream().map(Car::getPrice).min(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
            BigDecimal max = brandCars.stream().map(Car::getPrice).max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
            return "💰 " + brand + " cars range from $" + min + " to $" + max + ".";
        }

        String models = brandCars.stream()
                .limit(3)
                .map(c -> c.getYear() + " " + c.getModel() + " ($" + c.getPrice() + ")")
                .collect(Collectors.joining(", "));

        return "✅ Here are our " + brand + " cars: " + models +
                (brandCars.size() > 3 ? " and " + (brandCars.size() - 3) + " more." : ".");
    }

    private String handleBudgetQuery(int budget, String query) {
        List<Car> carsUnderBudget = getAllAvailableCars().stream()
                .filter(c -> c.getPrice().doubleValue() <= budget)
                .sorted(Comparator.comparing(Car::getPrice))
                .limit(5)
                .collect(Collectors.toList());

        if (carsUnderBudget.isEmpty()) {
            return "💰 No cars found under $" + budget + ". Try increasing your budget!";
        }

        String brand = extractBrand(query);
        if (brand != null) {
            carsUnderBudget = carsUnderBudget.stream()
                    .filter(c -> c.getMake().equalsIgnoreCase(brand))
                    .collect(Collectors.toList());
            if (carsUnderBudget.isEmpty()) {
                return "No " + brand + " cars under $" + budget + ".";
            }
        }

        String cars = carsUnderBudget.stream()
                .map(c -> "• " + c.getYear() + " " + c.getMake() + " " + c.getModel() +
                        " - $" + c.getPrice())
                .collect(Collectors.joining("\n"));

        return "💰 Cars under $" + budget + ":\n\n" + cars;
    }

    private String getPriceRange() {
        List<Car> cars = getAllAvailableCars();
        if (cars.isEmpty()) return "No cars available.";

        BigDecimal min = cars.stream().map(Car::getPrice).min(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        BigDecimal max = cars.stream().map(Car::getPrice).max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);

        return "💰 Our cars range from $" + min + " to $" + max + ".";
    }

    private String getCheapestCar() {
        Car cheapest = getAllAvailableCars().stream()
                .min(Comparator.comparing(Car::getPrice))
                .orElse(null);

        if (cheapest == null) return "No cars available.";

        return "💰 The cheapest car is the " + cheapest.getYear() + " " +
                cheapest.getMake() + " " + cheapest.getModel() + " for $" + cheapest.getPrice() + ".";
    }

    private String getMostExpensiveCar() {
        Car expensive = getAllAvailableCars().stream()
                .max(Comparator.comparing(Car::getPrice))
                .orElse(null);

        if (expensive == null) return "No cars available.";

        return "💎 The most expensive car is the " + expensive.getYear() + " " +
                expensive.getMake() + " " + expensive.getModel() + " for $" + expensive.getPrice() + ".";
    }

    private String getCarsByType(String type) {
        List<Car> cars = getAllAvailableCars().stream()
                .filter(c -> c.getBodyType().equalsIgnoreCase(type))
                .collect(Collectors.toList());

        if (cars.isEmpty()) return "No " + type + " cars available.";

        String models = cars.stream().limit(3)
                .map(c -> c.getYear() + " " + c.getMake() + " " + c.getModel() + " ($" + c.getPrice() + ")")
                .collect(Collectors.joining(", "));

        return "🚗 " + type + " cars: " + models;
    }

    private String getCarsByFuelType(String fuelType) {
        long count = getAllAvailableCars().stream()
                .filter(c -> c.getFuel().equalsIgnoreCase(fuelType))
                .count();

        return "🚗 We have " + count + " " + fuelType + " car(s) available.";
    }

    private String getAvailabilityInfo() {
        long count = getAllAvailableCars().size();
        if (count > 0) {
            return "✅ Yes! We have " + count + " cars available right now.";
        }
        return "❌ No cars are available right now. Please check back later!";
    }

    private String extractBrand(String text) {
        Map<String, List<String>> brandPatterns = new HashMap<>();
        brandPatterns.put("BMW", Arrays.asList("bmw", "bwm", "beemer", "bimmer"));
        brandPatterns.put("Toyota", Arrays.asList("toyota", "toyta", "yota"));
        brandPatterns.put("Honda", Arrays.asList("honda", "hond"));
        brandPatterns.put("Tesla", Arrays.asList("tesla", "telsa"));
        brandPatterns.put("Mercedes", Arrays.asList("mercedes", "merc", "benz"));
        brandPatterns.put("Audi", Arrays.asList("audi", "awdi"));
        brandPatterns.put("Ford", Arrays.asList("ford", "frod"));

        for (Map.Entry<String, List<String>> entry : brandPatterns.entrySet()) {
            for (String pattern : entry.getValue()) {
                if (text.contains(pattern)) {
                    return entry.getKey();
                }
            }
        }
        return null;
    }

    private Integer extractBudget(String text) {
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(?:under|below|less than)\\s*\\$?(\\d+)");
        java.util.regex.Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private String extractVehicleType(String text) {
        if (text.contains("suv")) return "SUV";
        if (text.contains("sedan")) return "Sedan";
        if (text.contains("truck")) return "Truck";
        return null;
    }

    private String extractFuelType(String text) {
        if (text.contains("electric") || text.contains("ev")) return "Electric";
        if (text.contains("hybrid")) return "Hybrid";
        return null;
    }

    public List<Car> getAllAvailableCars() {
        return carRepository.findByStatus("AVAILABLE");
    }
}