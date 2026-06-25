// src/main/java/com/Shubham/carDealership/controller/AuthController.java
package com.Shubham.carDealership.controller;

import com.Shubham.carDealership.config.JwtUtil;
import com.Shubham.carDealership.dto.*;
import com.Shubham.carDealership.model.User;
import com.Shubham.carDealership.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = {"https://ai-car-dealership-frontend.onrender.com", "http://localhost:5173", "http://localhost:3000"})
public class AuthController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private PasswordEncoder passwordEncoder;

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

    private UserDto mapToUserDto(User user) {
        UserDto dto = new UserDto();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setEmail(user.getEmail());
        dto.setRole(user.getRole());
        dto.setPhoneNumber(user.getPhoneNumber()); // NEW
        return dto;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {
        // Check if email already exists
        if (userRepository.existsByEmail(request.getEmail())) {
            return ResponseEntity.ok(AuthResponse.error("Email already registered"));
        }

        // Check if username already exists
        if (userRepository.existsByUsername(request.getUsername())) {
            return ResponseEntity.ok(AuthResponse.error("Username already taken"));
        }

        // Create new user
        User user = new User();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setPhoneNumber(request.getPhoneNumber()); // NEW — optional, may be null/blank

        User savedUser = userRepository.save(user);

        // Generate token
        String token = jwtUtil.generateToken(savedUser.getId(), savedUser.getEmail());

        return ResponseEntity.ok(AuthResponse.success(mapToUserDto(savedUser), token));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElse(null);

        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            return ResponseEntity.ok(AuthResponse.error("Invalid email or password"));
        }

        String token = jwtUtil.generateToken(user.getId(), user.getEmail());

        return ResponseEntity.ok(AuthResponse.success(mapToUserDto(user), token));
    }

    // NEW: lets an already-logged-in user set or update their phone number
    // and/or username. This is what existing accounts (registered before
    // this field existed, or who left it blank at signup) use to unlock
    // the "Call" button on their car listings — re-listing a car after
    // updating will pick it up.
    @PutMapping("/profile")
    public ResponseEntity<?> updateProfile(@RequestBody Map<String, String> payload, HttpServletRequest httpRequest) {
        User user = getAuthenticatedUser(httpRequest);
        if (user == null) {
            return ResponseEntity.ok(Map.of("success", false, "message", "Please login first"));
        }

        if (payload.containsKey("username")) {
            String newUsername = payload.get("username") != null ? payload.get("username").trim() : "";
            if (!newUsername.isEmpty() && !newUsername.equals(user.getUsername())) {
                if (newUsername.length() < 3 || newUsername.length() > 50) {
                    return ResponseEntity.ok(Map.of("success", false, "message", "Username must be between 3 and 50 characters"));
                }
                if (userRepository.existsByUsername(newUsername)) {
                    return ResponseEntity.ok(Map.of("success", false, "message", "Username already taken"));
                }
                user.setUsername(newUsername);
            }
        }

        if (payload.containsKey("phoneNumber")) {
            user.setPhoneNumber(payload.get("phoneNumber"));
        }

        User saved = userRepository.save(user);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("user", mapToUserDto(saved));
        return ResponseEntity.ok(response);
    }
}