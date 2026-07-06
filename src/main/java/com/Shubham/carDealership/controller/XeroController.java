package com.Shubham.carDealership.controller;

import com.Shubham.carDealership.config.JwtUtil;
import com.Shubham.carDealership.model.User;
import com.Shubham.carDealership.repository.UserRepository;
import com.Shubham.carDealership.service.XeroService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/xero")
public class XeroController {

    @Autowired private XeroService     xeroService;
    @Autowired private JwtUtil         jwtUtil;
    @Autowired private UserRepository  userRepo;

    @Value("${app.frontend.url:http://localhost:5173}")
    private String frontendUrl;

    private Long ownerId(HttpServletRequest req) {
        String h = req.getHeader("Authorization");
        if (h != null && h.startsWith("Bearer ")) {
            String token = h.substring(7);
            if (jwtUtil.validateToken(token)) return jwtUtil.extractUserId(token);
        }
        return null;
    }

    /** Returns the Xero OAuth2 authorization URL for the current user */
    @GetMapping("/connect")
    public ResponseEntity<?> connect(HttpServletRequest req) {
        Long userId = ownerId(req);
        if (userId == null) return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        if (!xeroService.isConfigured())
            return ResponseEntity.status(503).body(Map.of("error", "Xero not configured"));
        return ResponseEntity.ok(Map.of("authUrl", xeroService.buildAuthUrl(userId)));
    }

    /** Xero redirects here after user authorizes — exchanges code and saves tokens */
    @GetMapping("/callback")
    public void callback(@RequestParam String code,
                         @RequestParam(required = false) String state,
                         HttpServletResponse res) throws IOException {
        try {
            Long userId = Long.parseLong(state);
            User user   = userRepo.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            Map<String, Object> tokens    = xeroService.exchangeCode(code);
            String              accessTok = String.valueOf(tokens.get("access_token"));
            String              refreshTok= String.valueOf(tokens.get("refresh_token"));

            Map<String, String> tenant = xeroService.getTenantInfo(accessTok);

            user.setXeroRefreshToken(refreshTok);
            user.setXeroTenantId(tenant.get("tenantId"));
            user.setXeroOrgName(tenant.getOrDefault("orgName", "Xero Organisation"));
            userRepo.save(user);

            res.sendRedirect(frontendUrl + "/app/settings?xero=connected");
        } catch (Exception e) {
            res.sendRedirect(frontendUrl + "/app/settings?xero=error");
        }
    }

    /** Returns whether the current user has Xero connected */
    @GetMapping("/status")
    public ResponseEntity<?> status(HttpServletRequest req) {
        Long userId = ownerId(req);
        if (userId == null) return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        User user = userRepo.findById(userId).orElse(null);
        if (user == null) return ResponseEntity.status(404).body(Map.of("error", "User not found"));
        return ResponseEntity.ok(Map.of(
                "connected", user.getXeroRefreshToken() != null,
                "orgName",   user.getXeroOrgName() != null ? user.getXeroOrgName() : ""
        ));
    }

    /** Disconnects Xero by clearing stored tokens */
    @PostMapping("/disconnect")
    public ResponseEntity<?> disconnect(HttpServletRequest req) {
        Long userId = ownerId(req);
        if (userId == null) return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        User user = userRepo.findById(userId).orElse(null);
        if (user == null) return ResponseEntity.notFound().build();
        user.setXeroRefreshToken(null);
        user.setXeroTenantId(null);
        user.setXeroOrgName(null);
        userRepo.save(user);
        return ResponseEntity.ok(Map.of("disconnected", true));
    }
}
