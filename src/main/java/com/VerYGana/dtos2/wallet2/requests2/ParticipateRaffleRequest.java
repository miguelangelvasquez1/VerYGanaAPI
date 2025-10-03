package com.VerYGana.dtos2.wallet2.requests2;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ParticipateRaffleRequest(
        @NotNull(message = "The userId cannot be null") @Positive(message = "The userId must be positive") Long userId,
        @NotNull(message = "The amount cannot be null") @DecimalMin(value = "0.01", inclusive = false, message = "The amount must be greater than zero") BigDecimal amount) {

}
