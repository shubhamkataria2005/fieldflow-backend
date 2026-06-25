// src/main/java/com/Shubham/carDealership/controller/ServiceController.java
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

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/service")
@CrossOrigin(origins = "http://localhost:5173")
public class ServiceController {

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

    private String describeCar(Long carId) {
        if (carId == null) return "your vehicle";
        Car car = carRepository.findById(carId).orElse(null);
        if (car == null) return "your vehicle";
        return car.getYear() + " " + car.getMake() + " " + car.getModel();
    }

    @PostMapping("/book")
    public ResponseEntity<?> bookAppointment(@RequestBody Map<String, Object> bookingData, HttpServletRequest request) {
        User user = getAuthenticatedUser(request);
        if (user == null) {
            return ResponseEntity.ok(Map.of("success", false, "message", "Please login to book appointments"));
        }

        try {
            ServiceAppointment appointment = new ServiceAppointment();
            appointment.setUserId(user.getId());

            if (bookingData.containsKey("carId") && bookingData.get("carId") != null && !bookingData.get("carId").toString().isEmpty()) {
                appointment.setCarId(Long.parseLong(bookingData.get("carId").toString()));
            }

            appointment.setServiceType(bookingData.get("serviceType").toString());
            appointment.setAppointmentDate(LocalDateTime.parse(bookingData.get("appointmentDate").toString()));

            if (bookingData.containsKey("notes")) {
                appointment.setNotes(bookingData.get("notes").toString());
            }

            appointment.setStatus("SCHEDULED");
            appointment.setCreatedAt(LocalDateTime.now());

            ServiceAppointment saved = appointmentRepository.save(appointment);

            // NEW: let the user know their request reached us and is pending
            // admin approval. Wrapped so a mail-server hiccup never affects
            // the booking itself (already saved above).
            emailService.sendTestDriveBooked(
                    user.getEmail(),
                    user.getUsername(),
                    describeCar(saved.getCarId()),
                    saved.getAppointmentDate()
            );

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("appointment", saved);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("success", false, "message", "Failed to book appointment: " + e.getMessage()));
        }
    }

    @GetMapping("/my-appointments")
    public ResponseEntity<?> getMyAppointments(HttpServletRequest request) {
        User user = getAuthenticatedUser(request);
        if (user == null) {
            return ResponseEntity.ok(Map.of("success", false, "message", "Please login"));
        }

        List<ServiceAppointment> appointments = appointmentRepository.findByUserId(user.getId());
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("appointments", appointments);
        return ResponseEntity.ok(response);
    }
}