package com.verygana2.dtos.user;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class SellerRegisterDTO {

    @Email(message = "Invalid email format")
    @NotBlank(message = "Email is required")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 6, message = "Password must have at least 6 characters")
    private String password;

    @NotBlank(message = "Phone number is required")
    private String phoneNumber;

    @NotBlank(message = "Shop name is required")
    private String shopName;

    @NotBlank(message = "Tax ID is required")
    private String taxId;

    @PositiveOrZero(message = "Total products must be zero or positive")
    private int totalProducts;

    @NotBlank(message = "Delivery region is required")
    private String deliveryRegion;
}
