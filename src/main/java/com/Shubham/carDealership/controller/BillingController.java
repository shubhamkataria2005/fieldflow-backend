package com.Shubham.carDealership.controller;

import com.Shubham.carDealership.config.JwtUtil;
import com.Shubham.carDealership.model.User;
import com.Shubham.carDealership.repository.UserRepository;
import com.stripe.Stripe;
import com.stripe.model.*;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.*;
import com.stripe.param.checkout.SessionCreateParams;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/billing")
public class BillingController {

    @Autowired private UserRepository userRepo;
    @Autowired private JwtUtil jwtUtil;

    @Value("${stripe.secret.key:}") private String secretKey;
    @Value("${stripe.webhook.secret:}") private String webhookSecret;
    @Value("${stripe.price.pro:}") private String pricePro;
    @Value("${stripe.price.business:}") private String priceBusiness;
    @Value("${app.frontend.url:http://localhost:5173}") private String frontendUrl;

    @PostConstruct
    public void init() {
        if (secretKey != null && !secretKey.isBlank()) {
            Stripe.apiKey = secretKey;
            System.out.println("✅ Stripe billing initialized");
        } else {
            System.out.println("⚠️  Stripe secret key not configured");
        }
    }

    private User getUser(HttpServletRequest req) {
        String h = req.getHeader("Authorization");
        if (h != null && h.startsWith("Bearer ")) {
            String token = h.substring(7);
            if (jwtUtil.validateToken(token)) {
                return userRepo.findById(jwtUtil.extractUserId(token)).orElse(null);
            }
        }
        return null;
    }

    // ── Create Stripe Checkout session ────────────────────────────────────────
    @PostMapping("/create-checkout")
    public ResponseEntity<?> createCheckout(@RequestBody Map<String, String> body, HttpServletRequest req) {
        User user = getUser(req);
        if (user == null) return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));

        String plan = body.get("plan");
        String priceId = "PRO".equalsIgnoreCase(plan) ? pricePro : priceBusiness;

        if (priceId == null || priceId.isBlank())
            return ResponseEntity.badRequest().body(Map.of("error", "Plan not available"));

        try {
            // Get or create Stripe customer
            String customerId = user.getStripeCustomerId();
            if (customerId == null || customerId.isBlank()) {
                Customer customer = Customer.create(CustomerCreateParams.builder()
                        .setEmail(user.getEmail())
                        .setName(user.getBusinessName() != null ? user.getBusinessName() : user.getUsername())
                        .build());
                customerId = customer.getId();
                user.setStripeCustomerId(customerId);
                userRepo.save(user);
            }

            SessionCreateParams params = SessionCreateParams.builder()
                    .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                    .setCustomer(customerId)
                    .addLineItem(SessionCreateParams.LineItem.builder()
                            .setPrice(priceId)
                            .setQuantity(1L)
                            .build())
                    .setSuccessUrl(frontendUrl + "/app/settings?billing=success&session_id={CHECKOUT_SESSION_ID}")
                    .setCancelUrl(frontendUrl + "/app/settings?billing=cancelled")
                    .putMetadata("userId", String.valueOf(user.getId()))
                    .putMetadata("plan", plan.toUpperCase())
                    .build();

            Session session = Session.create(params);
            return ResponseEntity.ok(Map.of("url", session.getUrl()));

        } catch (Exception e) {
            System.err.println("❌ Stripe checkout error: " + e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("error", "Could not create checkout session"));
        }
    }

    // ── Verify checkout session after redirect (no webhook needed) ───────────
    @PostMapping("/verify-session")
    public ResponseEntity<?> verifySession(@RequestBody Map<String, String> body, HttpServletRequest req) {
        User user = getUser(req);
        if (user == null) return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));

        String sessionId = body.get("sessionId");
        if (sessionId == null || sessionId.isBlank())
            return ResponseEntity.badRequest().body(Map.of("error", "Missing sessionId"));

        try {
            Session session = Session.retrieve(sessionId);

            if (!"paid".equals(session.getPaymentStatus()) && !"no_payment_required".equals(session.getPaymentStatus()))
                return ResponseEntity.badRequest().body(Map.of("error", "Payment not completed"));

            String userId = session.getMetadata().get("userId");
            String plan   = session.getMetadata().get("plan");

            if (userId == null || !userId.equals(String.valueOf(user.getId())))
                return ResponseEntity.status(403).body(Map.of("error", "Session does not belong to this user"));

            user.setPlan(plan);
            user.setStripeSubscriptionId(session.getSubscription());
            userRepo.save(user);
            System.out.println("✅ Plan upgraded to " + plan + " for user " + userId + " via redirect verify");

            return ResponseEntity.ok(Map.of("plan", plan, "message", "Plan updated successfully"));
        } catch (Exception e) {
            System.err.println("❌ Verify session error: " + e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("error", "Could not verify session"));
        }
    }

    // ── Stripe Billing Portal (manage / cancel subscription) ──────────────────
    @GetMapping("/portal")
    public ResponseEntity<?> billingPortal(HttpServletRequest req) {
        User user = getUser(req);
        if (user == null) return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));

        if (user.getStripeCustomerId() == null || user.getStripeCustomerId().isBlank())
            return ResponseEntity.badRequest().body(Map.of("error", "No active subscription found"));

        try {
            com.stripe.model.billingportal.Session portal =
                    com.stripe.model.billingportal.Session.create(
                            com.stripe.param.billingportal.SessionCreateParams.builder()
                                    .setCustomer(user.getStripeCustomerId())
                                    .setReturnUrl(frontendUrl + "/app/settings")
                                    .build());
            return ResponseEntity.ok(Map.of("url", portal.getUrl()));
        } catch (Exception e) {
            System.err.println("❌ Stripe portal error: " + e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("error", "Could not open billing portal"));
        }
    }

    // ── Stripe Webhook — updates plan when payment succeeds / fails ───────────
    @PostMapping("/webhook")
    public ResponseEntity<String> webhook(@RequestBody String payload, HttpServletRequest req) {
        String sigHeader = req.getHeader("Stripe-Signature");

        Event event;
        try {
            if (webhookSecret != null && !webhookSecret.isBlank()) {
                event = Webhook.constructEvent(payload, sigHeader, webhookSecret);
            } else {
                event = Event.GSON.fromJson(payload, Event.class);
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Invalid signature");
        }

        switch (event.getType()) {
            case "checkout.session.completed" -> {
                Session session = (Session) event.getDataObjectDeserializer()
                        .getObject().orElse(null);
                if (session != null) {
                    String userId = session.getMetadata().get("userId");
                    String plan   = session.getMetadata().get("plan");
                    if (userId != null && plan != null) {
                        userRepo.findById(Long.valueOf(userId)).ifPresent(u -> {
                            u.setPlan(plan);
                            u.setStripeSubscriptionId(session.getSubscription());
                            userRepo.save(u);
                            System.out.println("✅ Plan upgraded to " + plan + " for user " + userId);
                        });
                    }
                }
            }
            case "customer.subscription.deleted" -> {
                Subscription sub = (Subscription) event.getDataObjectDeserializer()
                        .getObject().orElse(null);
                if (sub != null) {
                    userRepo.findByStripeSubscriptionId(sub.getId()).ifPresent(u -> {
                        u.setPlan("STARTER");
                        u.setStripeSubscriptionId(null);
                        userRepo.save(u);
                        System.out.println("⬇️  Plan downgraded to STARTER for user " + u.getId());
                    });
                }
            }
            case "invoice.payment_failed" -> {
                Invoice invoice = (Invoice) event.getDataObjectDeserializer()
                        .getObject().orElse(null);
                if (invoice != null && invoice.getSubscription() != null) {
                    userRepo.findByStripeSubscriptionId(invoice.getSubscription()).ifPresent(u -> {
                        System.out.println("⚠️  Payment failed for user " + u.getId() + " — plan kept until Stripe retries");
                    });
                }
            }
        }

        return ResponseEntity.ok("received");
    }
}
