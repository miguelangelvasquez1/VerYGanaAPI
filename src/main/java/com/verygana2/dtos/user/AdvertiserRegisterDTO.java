package com.verygana2.dtos.user;

import jakarta.validation.constraints.*;

public record AdvertiserRegisterDTO(

    @Email(message = "Invalid email format")
    @NotBlank(message = "Email is required")
    String email,

    @NotBlank(message = "Password is required")
    @Size(min = 6, message = "Password must have at least 6 characters")
    String password,

    @NotBlank(message = "Phone number is required")
    String phoneNumber,

    @NotBlank(message = "Company name is required")
    String companyName,

    @NotBlank(message = "NIT is required")
    String nit,

    @PositiveOrZero(message = "Ad budget must be zero or positive")
    Double adBuget
) {}
