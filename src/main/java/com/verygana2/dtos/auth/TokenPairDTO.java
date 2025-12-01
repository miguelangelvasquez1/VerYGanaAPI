package com.verygana2.dtos.auth;

import lombok.AllArgsConstructor;
import lombok.Data;

@AllArgsConstructor
@Data
public class TokenPairDTO {
    
    private String accessToken;
    private String refreshToken;
}
