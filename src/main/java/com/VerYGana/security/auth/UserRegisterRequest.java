package com.VerYGana.security.auth;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UserRegisterRequest {
    private String name;
    private String email;
    private String phoneNumber;
    private String password;
}
