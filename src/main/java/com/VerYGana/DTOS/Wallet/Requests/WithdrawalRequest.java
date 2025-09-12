package com.VerYGana.dtos.Wallet.Requests;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record WithdrawalRequest(
        @NotNull(message = "The ownerId cannot be null") @Positive(message = "The ownerId must be positive") Long ownerId,
        @NotNull(message = "The amount cannot be null") @DecimalMin(value = "0.01", inclusive = false, message = "The amount must be greater than zero") BigDecimal amount) {

}
