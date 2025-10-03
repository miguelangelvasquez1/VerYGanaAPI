package com.VerYGana.dtos2.auth;



import com.VerYGana.models.enums2.Role;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UserRegisterRequest {
    private String name;
    private Role role;
    private String email;
    private String phoneNumber;
    private String password;
}
