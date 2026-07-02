package com.Shubham.carDealership.fsm.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class JobRequest {
    private Long customerId;
    private Long technicianId;
    private String jobType;
    private String status;
    private String priority;
    private String address;
    private String description;
    private String notes;
    private String partsUsed;
    private BigDecimal amount;
    private LocalDateTime scheduledAt;
}
