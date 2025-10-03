package com.VerYGana.dtos2.auth;

import lombok.Data;

@Data
public class AuthRequest {
    private String identifier;
    private String password;
}
