package com.VerYGana.dtos2.wallet2.requests2;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public record WithdrawalRequest(
        @NotNull(message = "The amount cannot be null") @DecimalMin(value = "0.01", inclusive = false, message = "The amount must be greater than zero") BigDecimal amount) {

}
