package com.Shubham.carDealership.dto;

import lombok.Data;

@Data
public class UserDto {
    private Long id;
    private String username;
    private String email;
    private String role;
    private String phoneNumber;
    private String profilePhoto;
    private String businessName;
    private String businessAddress;
    private String businessAbn;
    private String invoiceNotes;
    private String plan;
}