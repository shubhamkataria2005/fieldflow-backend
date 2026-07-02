package com.Shubham.carDealership.fsm.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "fsm_technicians")
@Data
public class FsmTechnician {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "business_owner_id", nullable = false)
    private Long businessOwnerId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 20)
    private String phone;

    @Column(length = 100)
    private String email;

    // ON_JOB, DRIVING, AVAILABLE, OFF
    @Column(length = 20)
    private String status = "AVAILABLE";

    @Column(columnDefinition = "TEXT")
    private String skills;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
}
