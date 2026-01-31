package com.verygana2.dtos.auth;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RefreshRequest { // Only for mobile, also used in logout
    
    private String refreshToken;
}
