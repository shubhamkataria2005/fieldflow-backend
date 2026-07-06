package com.Shubham.carDealership.fsm.model;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "fsm_quotes")
@Data
public class FsmQuote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "business_owner_id", nullable = false)
    private Long businessOwnerId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "customer_id")
    private FsmCustomer customer;

    // DRAFT, SENT, ACCEPTED, DECLINED, EXPIRED
    @Column(nullable = false, length = 20)
    private String status = "DRAFT";

    @Column(length = 120)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(name = "gst_enabled")
    private boolean gstEnabled = true;

    @Column(name = "gst_rate", precision = 5, scale = 4)
    private BigDecimal gstRate = new BigDecimal("0.15");

    @Column(length = 500)
    private String notes;

    @Column(name = "valid_until")
    private LocalDateTime validUntil;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "accepted_at")
    private LocalDateTime acceptedAt;

    // When accepted, optionally link to a converted job
    @Column(name = "converted_job_id")
    private Long convertedJobId;
}
