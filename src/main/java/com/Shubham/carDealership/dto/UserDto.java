package com.Shubham.carDealership.dto;

import lombok.Data;

@Data
public class UserDto {
    private Long id;
    private String username;
    private String email;
    private String role;
    private String phoneNumber; // NEW
    private String profilePhoto; // NEW
}