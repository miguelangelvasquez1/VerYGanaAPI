package com.verygana2.dtos.auth;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CommercialRegisterRequest {
    private String companyName;
    private String email;
    private String phoneNumber;
    private String password;
}
