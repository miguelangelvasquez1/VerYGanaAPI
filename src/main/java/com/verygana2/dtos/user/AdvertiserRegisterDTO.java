package com.verygana2.dtos.user;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class AdvertiserRegisterDTO {

    @Email(message = "Invalid email format")
    @NotBlank(message = "Email is required")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 6, message = "Password must have at least 6 characters")
    private String password;

    @NotBlank(message = "Phone number is required")
    private String phoneNumber;

    @NotBlank(message = "Company name is required")
    private String companyName;

    @NotBlank(message = "NIT is required")
    private String nit;

    @PositiveOrZero(message = "Ad budget must be zero or positive")
    private Double adBuget;
}
