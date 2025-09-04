package com.VerYGana.DTOS.Wallet.Requests;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record WalletUserCreateRequest (
    @NotNull(message = "Owner ID is required")
    @Positive(message = "Owner ID must be positive")
    Long userId
){

    
}
