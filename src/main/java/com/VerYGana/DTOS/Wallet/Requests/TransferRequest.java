package com.VerYGana.dtos.Wallet.Requests;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record TransferRequest(
        @NotNull(message = "The senderId cannot be null") @Positive(message = "The senderId must be positive") Long senderId,

        @NotNull(message = "The amount cannot be null") @DecimalMin(value = "0.01", inclusive = false, message = "The amount must be greater than zero") BigDecimal amount,

        @NotNull(message = "The receiverId cannot be null") @Positive(message = "The receiverId must be positive") Long receiverId) {

}
