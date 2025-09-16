package com.VerYGana.dtos.auth;



import com.VerYGana.models.Enums.Role;

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
