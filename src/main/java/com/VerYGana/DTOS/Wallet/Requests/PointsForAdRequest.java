package com.VerYGana.DTOS.Wallet.Requests;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record PointsForAdRequest(
        @NotNull(message = "The userId cannot be null") @Positive(message = "The userId must be positive") Long userId,

        @NotNull(message = "The amount cannot be null") @DecimalMin(value = "0.01", inclusive = false, message = "The amount must be greater than zero") BigDecimal amount,

        @NotNull(message = "The advertiserId cannot be null") @Positive(message = "The advertiserId must be positive") Long advertiserId) {
}
