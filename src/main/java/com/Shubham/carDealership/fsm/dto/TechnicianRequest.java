package com.Shubham.carDealership.fsm.dto;

import lombok.Data;

@Data
public class TechnicianRequest {
    private String name;
    private String phone;
    private String email;
    private String status;
    private String skills;
    private boolean createLogin;
    private String password;
}
