package com.verygana2.dtos.auth;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class RefreshRequest { // Only for mobile
    
    private String refreshToken;
}
