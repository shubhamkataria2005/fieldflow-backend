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
import java.util.stream.Collectors;

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

    private static final List<String> ACTIVE_STATUSES = List.of("SCHEDULED", "CONFIRMED");

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
            String serviceType = bookingData.get("serviceType").toString();

            Long carId = null;
            if (bookingData.containsKey("carId") && bookingData.get("carId") != null && !bookingData.get("carId").toString().isEmpty()) {
                carId = Long.parseLong(bookingData.get("carId").toString());
            }

            LocalDateTime appointmentDate = LocalDateTime.parse(bookingData.get("appointmentDate").toString());

            // NEW: a test drive only makes sense as a formal admin-managed
            // booking for cars the dealership actually owns. Marketplace cars
            // belong to private sellers — admin has no authority to confirm
            // a test drive for a car they don't control.
            if ("TEST_DRIVE".equals(serviceType) && carId != null) {
                Car car = carRepository.findById(carId).orElse(null);
                if (car != null && "MARKETPLACE".equals(car.getCarSource())) {
                    return ResponseEntity.ok(Map.of(
                            "success", false,
                            "message", "This car is privately listed — please message the seller directly to arrange a test drive."
                    ));
                }

                // NEW: one active booking per user per car — direct them to
                // reschedule their existing booking instead of creating a duplicate.
                List<ServiceAppointment> existingForUser = appointmentRepository
                        .findByUserIdAndCarIdAndStatusIn(user.getId(), carId, ACTIVE_STATUSES);
                if (!existingForUser.isEmpty()) {
                    Map<String, Object> response = new HashMap<>();
                    response.put("success", false);
                    response.put("message", "You already have an active booking for this car. Please reschedule your existing booking instead.");
                    response.put("existingAppointment", existingForUser.get(0));
                    return ResponseEntity.ok(response);
                }

                // NEW: lock the slot — once anyone has this car+time pending
                // or confirmed, no one else can grab the same slot.
                boolean slotTaken = appointmentRepository.findByCarIdAndStatusIn(carId, ACTIVE_STATUSES)
                        .stream()
                        .anyMatch(a -> a.getAppointmentDate().equals(appointmentDate));
                if (slotTaken) {
                    return ResponseEntity.ok(Map.of(
                            "success", false,
                            "message", "This time slot is no longer available. Please choose a different time."
                    ));
                }
            }

            ServiceAppointment appointment = new ServiceAppointment();
            appointment.setUserId(user.getId());
            appointment.setCarId(carId);
            appointment.setServiceType(serviceType);
            appointment.setAppointmentDate(appointmentDate);

            if (bookingData.containsKey("notes")) {
                appointment.setNotes(bookingData.get("notes").toString());
            }

            appointment.setStatus("SCHEDULED");
            appointment.setCreatedAt(LocalDateTime.now());

            ServiceAppointment saved = appointmentRepository.save(appointment);

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

    // NEW: does the logged-in user already have an active booking for this
    // car? The car detail page uses this to show "you have a booking" +
    // reschedule, instead of a blank booking form.
    @GetMapping("/my-appointment-for-car/{carId}")
    public ResponseEntity<?> getMyAppointmentForCar(@PathVariable Long carId, HttpServletRequest request) {
        User user = getAuthenticatedUser(request);
        if (user == null) {
            return ResponseEntity.ok(Map.of("success", false, "message", "Please login"));
        }

        List<ServiceAppointment> active = appointmentRepository
                .findByUserIdAndCarIdAndStatusIn(user.getId(), carId, ACTIVE_STATUSES);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("appointment", active.isEmpty() ? null : active.get(0));
        return ResponseEntity.ok(response);
    }

    // NEW: which time slots are already taken for this car on this date?
    // date is a plain YYYY-MM-DD string. Lets the frontend grey out options
    // before the user even tries to submit one.
    @GetMapping("/booked-slots/{carId}")
    public ResponseEntity<?> getBookedSlots(@PathVariable Long carId, @RequestParam String date) {
        LocalDateTime startOfDay = LocalDateTime.parse(date + "T00:00:00");
        LocalDateTime endOfDay = startOfDay.plusDays(1);

        List<String> bookedTimes = appointmentRepository.findByCarIdAndStatusIn(carId, ACTIVE_STATUSES)
                .stream()
                .filter(a -> !a.getAppointmentDate().isBefore(startOfDay) && a.getAppointmentDate().isBefore(endOfDay))
                .map(a -> a.getAppointmentDate().toString())
                .collect(Collectors.toList());

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("bookedSlots", bookedTimes);
        return ResponseEntity.ok(response);
    }

    // NEW: change the date/time of an existing booking instead of creating a
    // duplicate. Goes back to SCHEDULED so admin re-confirms the new time.
    @PutMapping("/{appointmentId}/reschedule")
    public ResponseEntity<?> rescheduleAppointment(@PathVariable Long appointmentId,
                                                   @RequestBody Map<String, String> payload,
                                                   HttpServletRequest request) {
        User user = getAuthenticatedUser(request);
        if (user == null) {
            return ResponseEntity.ok(Map.of("success", false, "message", "Please login"));
        }

        ServiceAppointment appointment = appointmentRepository.findById(appointmentId).orElse(null);
        if (appointment == null) {
            return ResponseEntity.ok(Map.of("success", false, "message", "Booking not found"));
        }

        if (!appointment.getUserId().equals(user.getId())) {
            return ResponseEntity.ok(Map.of("success", false, "message", "You can only reschedule your own bookings"));
        }

        if (!ACTIVE_STATUSES.contains(appointment.getStatus())) {
            return ResponseEntity.ok(Map.of("success", false, "message", "This booking can no longer be rescheduled"));
        }

        String newDateStr = payload.get("appointmentDate");
        if (newDateStr == null || newDateStr.isEmpty()) {
            return ResponseEntity.ok(Map.of("success", false, "message", "New date/time is required"));
        }
        LocalDateTime newDate = LocalDateTime.parse(newDateStr);

        if (appointment.getCarId() != null) {
            boolean slotTaken = appointmentRepository.findByCarIdAndStatusIn(appointment.getCarId(), ACTIVE_STATUSES)
                    .stream()
                    .anyMatch(a -> !a.getId().equals(appointment.getId()) && a.getAppointmentDate().equals(newDate));
            if (slotTaken) {
                return ResponseEntity.ok(Map.of("success", false, "message", "That time slot is already booked. Please choose a different time."));
            }
        }

        appointment.setAppointmentDate(newDate);
        appointment.setStatus("SCHEDULED");
        ServiceAppointment saved = appointmentRepository.save(appointment);

        emailService.sendTestDriveRescheduled(
                user.getEmail(),
                user.getUsername(),
                describeCar(saved.getCarId()),
                saved.getAppointmentDate()
        );

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("appointment", saved);
        return ResponseEntity.ok(response);
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