package com.Shubham.carDealership.fsm.model;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "fsm_jobs")
@Data
public class FsmJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "business_owner_id", nullable = false)
    private Long businessOwnerId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "customer_id")
    private FsmCustomer customer;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "technician_id")
    private FsmTechnician technician;

    @Column(name = "job_type", nullable = false, length = 100)
    private String jobType;

    // NEW, SCHEDULED, DISPATCHED, IN_PROGRESS, COMPLETED, INVOICED
    @Column(nullable = false, length = 20)
    private String status = "NEW";

    // NORMAL, URGENT, EMERGENCY
    @Column(nullable = false, length = 20)
    private String priority = "NORMAL";

    @Column(length = 255)
    private String address;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(columnDefinition = "TEXT")
    private String partsUsed;

    @Column(precision = 10, scale = 2)
    private BigDecimal amount = BigDecimal.ZERO;

    @Column(name = "scheduled_at")
    private LocalDateTime scheduledAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "tracking_key", unique = true, length = 36)
    private String trackingKey;

    @PrePersist
    public void generateTrackingKey() {
        if (trackingKey == null) {
            trackingKey = UUID.randomUUID().toString();
        }
    }
}
