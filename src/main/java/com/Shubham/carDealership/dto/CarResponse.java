// src/main/java/com/Shubham/carDealership/dto/CarResponse.java
package com.Shubham.carDealership.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class CarResponse {
    private Long id;
    private String make;
    private String model;
    private Integer year;
    private BigDecimal price;
    private Integer mileage;
    private String fuel;
    private String transmission;
    private String bodyType;
    private String description;
    private String imageUrl;
    private Long sellerId;
    private String sellerName;
    private String sellerEmail;
    private String sellerPhone; // NEW: powers the "Call" button on Contact Dealer
    private String status;
    private LocalDateTime createdAt;

    // New fields for hybrid system
    private String carSource;
    private String stockNumber;
    private Boolean isCompanyOwned;
    private String inspectionStatus;
}