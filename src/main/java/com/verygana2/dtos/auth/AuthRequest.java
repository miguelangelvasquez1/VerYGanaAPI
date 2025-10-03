package com.verygana2.dtos.auth;

import lombok.Data;

@Data
public class AuthRequest {
    private String identifier;
    private String password;
}
