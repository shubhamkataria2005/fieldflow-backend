// src/main/java/com/Shubham/carDealership/model/Car.java
package com.Shubham.carDealership.model;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "cars")
@Data
public class Car {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String make;

    @Column(nullable = false)
    private String model;

    @Column(nullable = false)
    private Integer year;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(nullable = false)
    private Integer mileage;

    @Column(nullable = false)
    private String fuel;

    @Column(nullable = false)
    private String transmission;

    @Column(name = "body_type", nullable = false)
    private String bodyType;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "image_url")
    private String imageUrl;

    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    @Column(name = "seller_name", nullable = false)
    private String sellerName;

    @Column(name = "seller_email", nullable = false)
    private String sellerEmail;

    // NEW: seller's phone number, copied from User.phoneNumber at listing time.
    // Nullable since not every user has a phone number on file — the frontend
    // already disables the "Call" button gracefully when this is null.
    @Column(name = "seller_phone")
    private String sellerPhone;

    private String status = "AVAILABLE";

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    // NEW FIELDS FOR HYBRID SYSTEM
    @Column(name = "car_source")
    private String carSource = "MARKETPLACE"; // MARKETPLACE or DEALERSHIP

    @Column(name = "stock_number")
    private String stockNumber;

    @Column(name = "is_company_owned")
    private Boolean isCompanyOwned = false;

    @Column(name = "inspection_status")
    private String inspectionStatus = "PENDING"; // PENDING, PASSED, FAILED

    @Column(name = "sales_employee_id")
    private Long salesEmployeeId;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();
}