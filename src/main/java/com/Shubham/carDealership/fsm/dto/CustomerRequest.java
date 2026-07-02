package com.Shubham.carDealership.fsm.dto;

import lombok.Data;

@Data
public class CustomerRequest {
    private String name;
    private String phone;
    private String email;
    private String address;
    private String notes;
}
