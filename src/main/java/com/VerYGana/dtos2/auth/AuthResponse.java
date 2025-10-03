package com.VerYGana.dtos2.auth;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AuthResponse {
    
    private String accessToken;
    private String refreshToken;
}
