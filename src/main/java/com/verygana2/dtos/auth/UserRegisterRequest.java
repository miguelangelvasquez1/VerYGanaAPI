package com.verygana2.dtos.auth;



import com.verygana2.models.enums.Role;

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
