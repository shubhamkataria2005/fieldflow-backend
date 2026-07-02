package com.Shubham.carDealership.fsm.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class JobResponse {
    private Long id;
    private CustomerResponse customer;
    private TechnicianResponse technician;
    private String jobType;
    private String status;
    private String priority;
    private String address;
    private String description;
    private String notes;
    private String partsUsed;
    private BigDecimal amount;
    private LocalDateTime scheduledAt;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
    private String trackingKey;
}
