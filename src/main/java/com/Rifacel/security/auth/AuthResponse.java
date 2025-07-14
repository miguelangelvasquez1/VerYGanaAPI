package com.Rifacel.security.auth;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AuthResponse {
    
    private String token;
    // private String refreshToken; Implement later if needed
}
