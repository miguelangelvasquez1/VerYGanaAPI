package com.VerYGana.security.auth;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor

public class UserLoginRequest {
    private String identifier;
    private String password;
}