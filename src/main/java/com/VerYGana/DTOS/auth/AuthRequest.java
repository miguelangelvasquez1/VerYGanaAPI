package com.VerYGana.dtos.auth;

import lombok.Data;

@Data
public class AuthRequest {
    private String identifier;
    private String password;
}
