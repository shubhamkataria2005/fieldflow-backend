// src/main/java/com/Shubham/carDealership/model/User.java
package com.Shubham.carDealership.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Data
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 50)
    private String username;

    @Column(unique = true, nullable = false, length = 100)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String role = "USER"; // USER, ADMIN, SUPER_ADMIN, SALES_EMPLOYEE

    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "last_login")
    private LocalDateTime lastLogin;

    // NEW FIELDS FOR EMPLOYEES
    @Column(name = "is_employee")
    private Boolean isEmployee = false;

    @Column(name = "employee_id")
    private String employeeId;

    @Column(name = "department")
    private String department; // SALES, SERVICE, SUPPORT

    @Column(name = "phone_number")
    private String phoneNumber;

    @Column(name = "profile_photo", columnDefinition = "TEXT")
    private String profilePhoto;

    // FieldFlow FSM fields
    @Column(name = "business_name", length = 120)
    private String businessName;

    @Column(name = "business_address", length = 255)
    private String businessAddress;

    @Column(name = "business_abn", length = 50)
    private String businessAbn;

    @Column(name = "invoice_notes", columnDefinition = "TEXT")
    private String invoiceNotes;

    @Column(name = "plan", length = 20)
    private String plan = "STARTER"; // STARTER, PRO, BUSINESS

    @Column(name = "stripe_customer_id", length = 60)
    private String stripeCustomerId;

    @Column(name = "stripe_subscription_id", length = 60)
    private String stripeSubscriptionId;

    // Xero integration
    @Column(name = "xero_refresh_token", columnDefinition = "TEXT")
    private String xeroRefreshToken;

    @Column(name = "xero_tenant_id", length = 80)
    private String xeroTenantId;

    @Column(name = "xero_org_name", length = 120)
    private String xeroOrgName;
}