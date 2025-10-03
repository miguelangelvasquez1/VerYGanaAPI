package com.VerYGana.dtos2.wallet2.requests2;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UnblockBalanceRequest(
        @NotNull(message = "The amount cannot be null") @DecimalMin(value = "0.01", inclusive = false, message = "The amount must be greater than zero") BigDecimal amount,
        @NotNull(message = "The reason cannot be null") @NotBlank(message = "The reason cannot be empty") @Size(max = 255, message = "The reason cannot exceed 250 characters") String reason)

{

}
