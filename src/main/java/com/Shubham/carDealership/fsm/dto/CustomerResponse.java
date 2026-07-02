package com.Shubham.carDealership.fsm.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class CustomerResponse {
    private Long id;
    private String name;
    private String phone;
    private String email;
    private String address;
    private String notes;
    private LocalDateTime createdAt;
}
