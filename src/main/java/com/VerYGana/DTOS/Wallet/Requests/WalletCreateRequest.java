package com.VerYGana.dtos.Wallet.Requests;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record WalletCreateRequest (
    @NotNull(message = "Owner Id is required")
    @Positive(message = "Owner Id must be positive")
    Long ownerId
){

    
}
