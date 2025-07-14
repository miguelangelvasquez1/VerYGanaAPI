package com.Rifacel.security.auth;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UserRegisterRequest {
    
    private String name;
    private String email;
    private String phoneNumber;
    private String department;
    private String municipality;
    private String address;
    private String password;
}
