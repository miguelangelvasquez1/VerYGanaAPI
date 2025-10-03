package com.VerYGana.dtos2.wallet2.requests2;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record WalletAdvertiserCreateRequest(
    @NotNull(message = "The advertiserId cannot be null")
    @Positive(message = "The advertiserId must be greater than zero")
    Long advertiserId
) {
} 
