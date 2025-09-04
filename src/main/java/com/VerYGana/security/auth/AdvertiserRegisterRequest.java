package com.VerYGana.security.auth;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AdvertiserRegisterRequest {
    private String companyName;
    private String email;
    private String phoneNumber;
    private String password;
}
