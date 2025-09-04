package com.VerYGana.DTOS.Wallet.Requests;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record WalletAdvertiserCreateRequest(
    @NotNull(message = "The advertiserId cannot be null")
    @Positive(message = "The advertiserId must be greater than zero")
    Long advertiserId
) {
} 
