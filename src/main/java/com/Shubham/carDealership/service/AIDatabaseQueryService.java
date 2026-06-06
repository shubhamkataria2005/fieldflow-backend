package com.Shubham.carDealership.service;

import com.Shubham.carDealership.model.Car;
import com.Shubham.carDealership.repository.CarRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AIDatabaseQueryService {

    @Autowired
    private CarRepository carRepository;

    public List<Car> getAllAvailableCars() {
        return carRepository.findByStatus("AVAILABLE");
    }

    public String handleNaturalLanguageQuery(String userMessage) {
        String lower = userMessage.toLowerCase();

        // Count queries
        if (lower.contains("how many") || lower.contains("count") ||
                lower.contains("total") || lower.contains("number of")) {
            return getCarCount(lower);
        }

        // Brand-specific queries (handles typos like "bwm" for BMW)
        String brand = extractBrandWithFlexibleMatching(lower);
        if (brand != null) {
            return getBrandInfo(brand, lower);
        }

        // Price queries
        if (lower.contains("price") || lower.contains("cost") ||
                lower.contains("how much") || lower.contains("cheapest") ||
                lower.contains("expensive")) {
            return getPriceInfo(lower);
        }

        // Availability queries
        if (lower.contains("available") || lower.contains("have") ||
                lower.contains("got") || lower.contains("any")) {
            return getAvailabilityInfo(lower);
        }

        return null;
    }

    private String getCarCount(String query) {
        List<Car> allCars = getAllAvailableCars();
        long dealershipCount = allCars.stream()
                .filter(c -> "DEALERSHIP".equals(c.getCarSource()))
                .count();
        long marketplaceCount = allCars.stream()
                .filter(c -> "MARKETPLACE".equals(c.getCarSource()))
                .count();

        if (query.contains("dealership")) {
            if (dealershipCount == 0) {
                return "🏢 Currently, there are no cars in our dealership inventory. Please check back later!";
            }
            return "🏢 We have " + dealershipCount + " car(s) in our dealership inventory.";
        } else if (query.contains("marketplace")) {
            if (marketplaceCount == 0) {
                return "🛒 There are no cars listed on our marketplace right now. Be the first to list one!";
            }
            return "🛒 There are " + marketplaceCount + " car(s) listed on our marketplace.";
        } else {
            if (allCars.isEmpty()) {
                return "🚗 No cars are available right now. Please check back later!";
            }
            return "🚗 We have " + allCars.size() + " car(s) total (" +
                    dealershipCount + " from dealership, " +
                    marketplaceCount + " from marketplace).";
        }
    }

    private String extractBrandWithFlexibleMatching(String text) {
        Map<String, List<String>> brandPatterns = new HashMap<>();
        brandPatterns.put("BMW", Arrays.asList("bmw", "bwm", "beemer", "bimmer", "b m w"));
        brandPatterns.put("Toyota", Arrays.asList("toyota", "toyta", "yota", "toyoda"));
        brandPatterns.put("Honda", Arrays.asList("honda", "hond", "hondo"));
        brandPatterns.put("Tesla", Arrays.asList("tesla", "telsa", "tesler"));
        brandPatterns.put("Mercedes", Arrays.asList("mercedes", "merc", "benz"));
        brandPatterns.put("Audi", Arrays.asList("audi", "awdi", "aude"));
        brandPatterns.put("Ford", Arrays.asList("ford", "frod", "furd"));
        brandPatterns.put("Hyundai", Arrays.asList("hyundai", "hundai"));
        brandPatterns.put("Kia", Arrays.asList("kia", "kiya"));
        brandPatterns.put("Mazda", Arrays.asList("mazda", "mazada"));
        brandPatterns.put("Subaru", Arrays.asList("subaru", "subrau"));
        brandPatterns.put("Nissan", Arrays.asList("nissan", "nisan"));

        for (Map.Entry<String, List<String>> entry : brandPatterns.entrySet()) {
            for (String pattern : entry.getValue()) {
                if (text.contains(pattern)) {
                    return entry.getKey();
                }
            }
        }
        return null;
    }

    private String getBrandInfo(String brand, String query) {
        List<Car> brandCars = getAllAvailableCars().stream()
                .filter(c -> c.getMake().equalsIgnoreCase(brand))
                .collect(Collectors.toList());

        if (brandCars.isEmpty()) {
            return "🚗 Sorry, we don't have any " + brand + " cars available right now.";
        }

        if (query.contains("how many") || query.contains("count")) {
            return "We have " + brandCars.size() + " " + brand + " car(s) available.";
        }

        String models = brandCars.stream()
                .limit(3)
                .map(c -> c.getYear() + " " + c.getModel() + " ($" + c.getPrice() + ")")
                .collect(Collectors.joining(", "));

        return "✅ Yes! We have " + brandCars.size() + " " + brand +
                " car(s): " + models + ".";
    }

    private String getPriceInfo(String query) {
        List<Car> availableCars = getAllAvailableCars();
        if (availableCars.isEmpty()) {
            return "No cars available for pricing right now.";
        }

        if (query.contains("cheapest")) {
            Car cheapest = availableCars.stream()
                    .min(Comparator.comparing(Car::getPrice))
                    .orElse(null);
            if (cheapest != null) {
                return "💰 The cheapest car is the " + cheapest.getYear() + " " +
                        cheapest.getMake() + " " + cheapest.getModel() + " for $" + cheapest.getPrice() + "!";
            }
        }

        if (query.contains("expensive")) {
            Car mostExpensive = availableCars.stream()
                    .max(Comparator.comparing(Car::getPrice))
                    .orElse(null);
            if (mostExpensive != null) {
                return "💎 The most expensive car is the " + mostExpensive.getYear() + " " +
                        mostExpensive.getMake() + " " + mostExpensive.getModel() + " for $" + mostExpensive.getPrice() + "!";
            }
        }

        BigDecimal minPrice = availableCars.stream()
                .map(Car::getPrice)
                .min(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);

        BigDecimal maxPrice = availableCars.stream()
                .map(Car::getPrice)
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);

        return "💰 Our cars range from $" + minPrice + " to $" + maxPrice + ".";
    }

    private String getAvailabilityInfo(String query) {
        List<Car> availableCars = getAllAvailableCars();
        if (availableCars.isEmpty()) {
            return "📭 Currently, no cars are available. Please check back later!";
        }

        String brand = extractBrandWithFlexibleMatching(query);
        if (brand != null) {
            long count = availableCars.stream()
                    .filter(c -> c.getMake().equalsIgnoreCase(brand))
                    .count();
            if (count > 0) {
                return "✅ Yes, we have " + count + " " + brand + " car(s) available!";
            } else {
                return "❌ Sorry, no " + brand + " cars are available right now.";
            }
        }

        return "✅ Yes! We have " + availableCars.size() + " cars available right now!";
    }
}