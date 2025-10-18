package com.verygana2.dtos.user;

import jakarta.validation.constraints.*;

public record ConsumerRegisterDTO(

    @Email(message = "Invalid email format")
    @NotBlank(message = "Email is required")
    String email,

    @NotBlank(message = "Password is required")
    @Size(min = 6, message = "Password must have at least 6 characters")
    String password,

    @NotBlank(message = "Phone number is required")
    String phoneNumber,

    @NotBlank(message = "Name is required")
    String name,

    @NotBlank(message = "Last name is required")
    String lastName,

    @NotBlank(message = "Department is required")
    String department,

    @NotBlank(message = "Municipio is required")
    String municipio
) {}