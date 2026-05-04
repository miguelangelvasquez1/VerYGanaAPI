package com.verygana2.dtos.wallet.requests;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record DepositRequest(
        @NotNull(message = "Amount is required")
        @Min(value = 500000, message = "Minimum deposit is 5,000 COP")
        @Max(value = 500000000, message = "Maximum deposit is 5,000,000 COP")
        Long amountCents) {
}
