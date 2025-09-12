package com.VerYGana.dtos.Wallet.Requests;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record BlockBalanceRequest(
        @NotNull(message = "The ownerId cannot be null") @Positive(message = "The ownerId must be positive") Long ownerId,

        @NotNull(message = "The amount cannot be null") @DecimalMin(value = "0.01", inclusive = false, message = "The amount to block must be greater than zero") BigDecimal amount,

        @NotNull(message = "The reason cannot be null") @NotBlank(message = "The reason cannot be empty") @Size(max = 255, message = "The reason cannot exceed 250 characters") String reason) {
}
