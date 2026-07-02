package com.Shubham.carDealership.fsm.model;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "fsm_invoices")
@Data
public class FsmInvoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "business_owner_id", nullable = false)
    private Long businessOwnerId;

    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "job_id")
    private FsmJob job;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "customer_id")
    private FsmCustomer customer;

    // UNPAID, PAID, OVERDUE
    @Column(nullable = false, length = 20)
    private String status = "UNPAID";

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount = BigDecimal.ZERO;

    @Column(length = 30)
    private String paymentMethod;

    @Column(name = "issued_at")
    private LocalDateTime issuedAt = LocalDateTime.now();

    @Column(name = "paid_at")
    private LocalDateTime paidAt;
}
