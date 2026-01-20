package com.verygana2.dtos.auth;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AuthResponse {
    
    private String accessToken;
    private String refreshToken; // Only for mobile
    private String role;
}
