package com.verygana2.dtos.user;

import jakarta.validation.constraints.*;

public record SellerRegisterDTO(

    @Email(message = "Invalid email format")
    @NotBlank(message = "Email is required")
    String email,

    @NotBlank(message = "Password is required")
    @Size(min = 6, message = "Password must have at least 6 characters")
    String password,

    @NotBlank(message = "Phone number is required")
    String phoneNumber,

    @NotBlank(message = "Shop name is required")
    String shopName,

    @NotBlank(message = "Tax ID is required")
    String taxId,

    @PositiveOrZero(message = "Total products must be zero or positive")
    int totalProducts,

    @PositiveOrZero(message = "Earnings must be zero or positive")
    double earnings,

    @NotBlank(message = "Delivery region is required")
    String deliveryRegion
) {}
