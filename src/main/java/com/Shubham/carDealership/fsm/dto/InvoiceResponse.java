package com.Shubham.carDealership.fsm.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class InvoiceResponse {
    private Long id;
    private Long jobId;
    private String jobType;
    private CustomerResponse customer;
    private String status;
    private BigDecimal amount;    // ex-GST subtotal
    private BigDecimal gstAmount; // computed: amount * gstRate if gstEnabled
    private BigDecimal total;     // amount + gstAmount
    private boolean gstEnabled;
    private BigDecimal gstRate;
    private String paymentMethod;
    private LocalDateTime issuedAt;
    private LocalDateTime paidAt;
}
