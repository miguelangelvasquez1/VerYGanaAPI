package com.verygana2.dtos.wallet.requests;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record WithdrawalRequest(
        @NotNull(message = "Amount is required")
        @Min(value = 1, message = "Amount must be greater than zero")
        Long amountCents) {
}
