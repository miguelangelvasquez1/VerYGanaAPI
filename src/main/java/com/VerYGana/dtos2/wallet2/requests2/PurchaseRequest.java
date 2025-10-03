package com.VerYGana.dtos2.wallet2.requests2;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record PurchaseRequest(
        @NotNull(message = "The buyerId cannot be null") @Positive(message = "The buyerId must be greater than zero") Long buyerId,

        @NotNull(message = "The amount cannot be null") @DecimalMin(value = "0.01", inclusive = false, message = "The amount must be greater than zero") BigDecimal amount,

        @NotNull(message = "The sellerId cannot be null") @Positive(message = "The sellerId must be greater than zero") Long sellerId) {
}
