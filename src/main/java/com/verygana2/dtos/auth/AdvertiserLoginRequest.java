package com.verygana2.dtos.auth;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AdvertiserLoginRequest {
    private String identifier;
    private String password;
}
