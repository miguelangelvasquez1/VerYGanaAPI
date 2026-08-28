package com.verygana2.dtos.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AuthRequest {
    private String identifier;
    private String password;
    @NotBlank
    private String recaptchaToken;
}
