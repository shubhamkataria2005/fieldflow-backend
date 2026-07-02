package com.Shubham.carDealership.fsm.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class TechnicianResponse {
    private Long id;
    private String name;
    private String phone;
    private String email;
    private String status;
    private String skills;
    private Long userId;
    private boolean hasLogin;
    private String tempPassword;
    private LocalDateTime createdAt;
}
