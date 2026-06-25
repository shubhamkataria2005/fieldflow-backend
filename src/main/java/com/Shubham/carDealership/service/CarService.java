// src/main/java/com/Shubham/carDealership/service/CarService.java
package com.Shubham.carDealership.service;

import com.Shubham.carDealership.dto.CarRequest;
import com.Shubham.carDealership.dto.CarResponse;
import com.Shubham.carDealership.model.Car;
import com.Shubham.carDealership.model.User;
import com.Shubham.carDealership.repository.CarRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class CarService {

    @Autowired
    private CarRepository carRepository;

    public CarResponse listCar(CarRequest request, User seller) {
        Car car = new Car();
        car.setMake(request.getMake());
        car.setModel(request.getModel());
        car.setYear(request.getYear());
        car.setPrice(request.getPrice());
        car.setMileage(request.getMileage());
        car.setFuel(request.getFuel());
        car.setTransmission(request.getTransmission());
        car.setBodyType(request.getBodyType());
        car.setDescription(request.getDescription());
        car.setImageUrl(request.getImageUrl());
        car.setSellerId(seller.getId());
        car.setSellerName(seller.getUsername());
        car.setSellerEmail(seller.getEmail());
        car.setSellerPhone(seller.getPhoneNumber()); // NEW
        car.setCarSource("MARKETPLACE");
        car.setIsCompanyOwned(false);
        car.setCreatedAt(LocalDateTime.now());
        car.setUpdatedAt(LocalDateTime.now());

        Car savedCar = carRepository.save(car);
        return mapToResponse(savedCar);
    }

    public CarResponse addDealershipCar(CarRequest request, User employee) {
        Car car = new Car();
        car.setMake(request.getMake());
        car.setModel(request.getModel());
        car.setYear(request.getYear());
        car.setPrice(request.getPrice());
        car.setMileage(request.getMileage());
        car.setFuel(request.getFuel());
        car.setTransmission(request.getTransmission());
        car.setBodyType(request.getBodyType());
        car.setDescription(request.getDescription());
        car.setImageUrl(request.getImageUrl());
        car.setSellerId(employee.getId());
        car.setSellerName(employee.getUsername());
        car.setSellerEmail(employee.getEmail());
        car.setSellerPhone(employee.getPhoneNumber()); // NEW
        car.setCarSource("DEALERSHIP");
        car.setIsCompanyOwned(true);
        car.setSalesEmployeeId(employee.getId());
        car.setStockNumber(generateStockNumber());
        car.setInspectionStatus("PENDING");
        car.setStatus("AVAILABLE");
        car.setCreatedAt(LocalDateTime.now());
        car.setUpdatedAt(LocalDateTime.now());

        Car savedCar = carRepository.save(car);
        return mapToResponse(savedCar);
    }

    public List<CarResponse> getAllAvailableCars() {
        return carRepository.findByStatus("AVAILABLE").stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public List<CarResponse> getDealershipInventory() {
        return carRepository.findByCarSourceAndStatus("DEALERSHIP", "AVAILABLE").stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public List<CarResponse> getMarketplaceListings() {
        return carRepository.findByCarSourceAndStatus("MARKETPLACE", "AVAILABLE").stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public List<CarResponse> getUserCars(Long userId) {
        return carRepository.findBySellerId(userId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public CarResponse getCarById(Long id) {
        Car car = carRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Car not found"));
        return mapToResponse(car);
    }

    // FIXED: Added keyword parameter and fixed the method signature
    public List<CarResponse> searchCars(String make, String bodyType, String fuel, Double maxPrice, String carSource) {
        return carRepository.findAll().stream()
                .filter(car -> {
                    // Status filter
                    if (!"AVAILABLE".equals(car.getStatus())) return false;

                    // Source filter
                    if (carSource != null && !carSource.isEmpty() && !carSource.equals(car.getCarSource())) return false;

                    // Make filter
                    if (make != null && !make.isEmpty() && !make.equals(car.getMake())) return false;

                    // Body type filter
                    if (bodyType != null && !bodyType.isEmpty() && !bodyType.equals(car.getBodyType())) return false;

                    // Fuel filter
                    if (fuel != null && !fuel.isEmpty() && !fuel.equals(car.getFuel())) return false;

                    // Price filter
                    if (maxPrice != null && car.getPrice().doubleValue() > maxPrice) return false;

                    return true;
                })
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    private String generateStockNumber() {
        return "STK-" + System.currentTimeMillis();
    }

    private CarResponse mapToResponse(Car car) {
        CarResponse response = new CarResponse();
        response.setId(car.getId());
        response.setMake(car.getMake());
        response.setModel(car.getModel());
        response.setYear(car.getYear());
        response.setPrice(car.getPrice());
        response.setMileage(car.getMileage());
        response.setFuel(car.getFuel());
        response.setTransmission(car.getTransmission());
        response.setBodyType(car.getBodyType());
        response.setDescription(car.getDescription());
        response.setImageUrl(car.getImageUrl());
        response.setSellerId(car.getSellerId());
        response.setSellerName(car.getSellerName());
        response.setSellerEmail(car.getSellerEmail());
        response.setSellerPhone(car.getSellerPhone()); // NEW
        response.setStatus(car.getStatus());
        response.setCreatedAt(car.getCreatedAt());
        response.setCarSource(car.getCarSource());
        response.setStockNumber(car.getStockNumber());
        response.setIsCompanyOwned(car.getIsCompanyOwned());
        response.setInspectionStatus(car.getInspectionStatus());
        return response;
    }
}