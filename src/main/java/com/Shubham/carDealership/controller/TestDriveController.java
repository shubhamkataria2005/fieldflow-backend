// src/main/java/com/Shubham/carDealership/controller/TestDriveController.java
package com.Shubham.carDealership.controller;

import com.Shubham.carDealership.config.JwtUtil;
import com.Shubham.carDealership.model.Car;
import com.Shubham.carDealership.model.ServiceAppointment;
import com.Shubham.carDealership.model.User;
import com.Shubham.carDealership.repository.CarRepository;
import com.Shubham.carDealership.repository.ServiceAppointmentRepository;
import com.Shubham.carDealership.repository.UserRepository;
import com.Shubham.carDealership.service.EmailService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/test-drives")
@CrossOrigin(origins = {"http://localhost:5173", "http://localhost:3000"})
public class TestDriveController {

    @Autowired
    private ServiceAppointmentRepository appointmentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CarRepository carRepository;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private EmailService emailService;

    private User getAuthenticatedUser(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            if (jwtUtil.validateToken(token)) {
                Long userId = jwtUtil.extractUserId(token);
                return userRepository.findById(userId).orElse(null);
            }
        }
        return null;
    }

    private boolean isAdmin(User user) {
        return user != null && ("ADMIN".equals(user.getRole()) || "SUPER_ADMIN".equals(user.getRole()));
    }

    private String describeCar(Long carId) {
        if (carId == null) return "your vehicle";
        Car car = carRepository.findById(carId).orElse(null);
        if (car == null) return "your vehicle";
        return car.getYear() + " " + car.getMake() + " " + car.getModel();
    }

    // Get all test drives (Admin only)
    @GetMapping("/all")
    public ResponseEntity<?> getAllTestDrives(HttpServletRequest request) {
        User admin = getAuthenticatedUser(request);
        if (!isAdmin(admin)) {
            return ResponseEntity.ok(Map.of("success", false, "message", "Admin access required"));
        }

        List<ServiceAppointment> allAppointments = appointmentRepository.findAll();

        // Enrich with user and car details
        List<Map<String, Object>> enriched = allAppointments.stream().map(apt -> {
            Map<String, Object> enrichedApt = new HashMap<>();
            enrichedApt.put("id", apt.getId());
            enrichedApt.put("serviceType", apt.getServiceType());
            enrichedApt.put("appointmentDate", apt.getAppointmentDate());
            enrichedApt.put("status", apt.getStatus());
            enrichedApt.put("notes", apt.getNotes());
            enrichedApt.put("createdAt", apt.getCreatedAt());

            User user = userRepository.findById(apt.getUserId()).orElse(null);
            enrichedApt.put("userName", user != null ? user.getUsername() : "Unknown");
            enrichedApt.put("userEmail", user != null ? user.getEmail() : "Unknown");

            if (apt.getCarId() != null) {
                Car car = carRepository.findById(apt.getCarId()).orElse(null);
                enrichedApt.put("carName", car != null ? car.getMake() + " " + car.getModel() : "Unknown");
                enrichedApt.put("carId", apt.getCarId());
            }

            return enrichedApt;
        }).collect(Collectors.toList());

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("appointments", enriched);
        return ResponseEntity.ok(response);
    }

    // Get pending test drives (Admin only)
    @GetMapping("/pending")
    public ResponseEntity<?> getPendingTestDrives(HttpServletRequest request) {
        User admin = getAuthenticatedUser(request);
        if (!isAdmin(admin)) {
            return ResponseEntity.ok(Map.of("success", false, "message", "Admin access required"));
        }

        List<ServiceAppointment> pendingAppointments = appointmentRepository.findByStatus("SCHEDULED");

        List<Map<String, Object>> enriched = pendingAppointments.stream().map(apt -> {
            Map<String, Object> enrichedApt = new HashMap<>();
            enrichedApt.put("id", apt.getId());
            enrichedApt.put("serviceType", apt.getServiceType());
            enrichedApt.put("appointmentDate", apt.getAppointmentDate());
            enrichedApt.put("status", apt.getStatus());
            enrichedApt.put("notes", apt.getNotes());

            User user = userRepository.findById(apt.getUserId()).orElse(null);
            enrichedApt.put("userName", user != null ? user.getUsername() : "Unknown");
            enrichedApt.put("userEmail", user != null ? user.getEmail() : "Unknown");

            if (apt.getCarId() != null) {
                Car car = carRepository.findById(apt.getCarId()).orElse(null);
                enrichedApt.put("carName", car != null ? car.getMake() + " " + car.getModel() : "Unknown");
            }

            return enrichedApt;
        }).collect(Collectors.toList());

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("appointments", enriched);
        return ResponseEntity.ok(response);
    }

    // Approve a test drive (Admin only)
    @PutMapping("/{appointmentId}/approve")
    public ResponseEntity<?> approveTestDrive(@PathVariable Long appointmentId, HttpServletRequest request) {
        User admin = getAuthenticatedUser(request);
        if (!isAdmin(admin)) {
            return ResponseEntity.ok(Map.of("success", false, "message", "Admin access required"));
        }

        ServiceAppointment appointment = appointmentRepository.findById(appointmentId).orElse(null);
        if (appointment == null) {
            return ResponseEntity.ok(Map.of("success", false, "message", "Appointment not found"));
        }

        appointment.setStatus("CONFIRMED");
        appointmentRepository.save(appointment);

        // NEW: tell the user their test drive is confirmed
        User bookingUser = userRepository.findById(appointment.getUserId()).orElse(null);
        if (bookingUser != null) {
            emailService.sendTestDriveConfirmed(
                    bookingUser.getEmail(),
                    bookingUser.getUsername(),
                    describeCar(appointment.getCarId()),
                    appointment.getAppointmentDate()
            );
        }

        return ResponseEntity.ok(Map.of("success", true, "message", "Test drive approved successfully"));
    }

    // Complete a test drive (Admin only)
    @PutMapping("/{appointmentId}/complete")
    public ResponseEntity<?> completeTestDrive(@PathVariable Long appointmentId, HttpServletRequest request) {
        User admin = getAuthenticatedUser(request);
        if (!isAdmin(admin)) {
            return ResponseEntity.ok(Map.of("success", false, "message", "Admin access required"));
        }

        ServiceAppointment appointment = appointmentRepository.findById(appointmentId).orElse(null);
        if (appointment == null) {
            return ResponseEntity.ok(Map.of("success", false, "message", "Appointment not found"));
        }

        appointment.setStatus("COMPLETED");
        appointmentRepository.save(appointment);

        return ResponseEntity.ok(Map.of("success", true, "message", "Test drive marked as completed"));
    }

    // Cancel a test drive (Admin only)
    @PutMapping("/{appointmentId}/cancel")
    public ResponseEntity<?> cancelTestDrive(@PathVariable Long appointmentId, HttpServletRequest request) {
        User admin = getAuthenticatedUser(request);
        if (!isAdmin(admin)) {
            return ResponseEntity.ok(Map.of("success", false, "message", "Admin access required"));
        }

        ServiceAppointment appointment = appointmentRepository.findById(appointmentId).orElse(null);
        if (appointment == null) {
            return ResponseEntity.ok(Map.of("success", false, "message", "Appointment not found"));
        }

        appointment.setStatus("CANCELLED");
        appointmentRepository.save(appointment);

        // NEW: tell the user their test drive was cancelled
        User bookingUser = userRepository.findById(appointment.getUserId()).orElse(null);
        if (bookingUser != null) {
            emailService.sendTestDriveCancelled(
                    bookingUser.getEmail(),
                    bookingUser.getUsername(),
                    describeCar(appointment.getCarId())
            );
        }

        return ResponseEntity.ok(Map.of("success", true, "message", "Test drive cancelled"));
    }
}