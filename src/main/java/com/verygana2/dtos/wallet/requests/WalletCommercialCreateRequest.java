package com.verygana2.dtos.wallet.requests;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record WalletCommercialCreateRequest(
    @NotNull(message = "The commercialId cannot be null")
    @Positive(message = "The commercialId must be greater than zero")
    Long commercialId
) {
} 
