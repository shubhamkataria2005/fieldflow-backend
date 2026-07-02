package com.Shubham.carDealership.fsm.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "fsm_job_messages")
@Data
public class JobMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // The tracking key ties the message to a job without exposing job ID to customers
    @Column(name = "tracking_key", nullable = false, length = 36)
    private String trackingKey;

    @Column(name = "job_id")
    private Long jobId;

    // "CUSTOMER" or "TECHNICIAN"
    @Column(name = "sender_type", nullable = false, length = 20)
    private String senderType;

    @Column(name = "sender_name", length = 100)
    private String senderName;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String message;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @PrePersist
    public void prePersist() {
        if (sentAt == null) sentAt = LocalDateTime.now();
    }
}
