package com.verygana2.dtos.wallet.requests;

import java.math.BigDecimal;

import com.verygana2.models.PaymentMethod;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public record DepositRequest(
        @NotNull(message = "Amount is required") @DecimalMin(value = "5000", message = "Minimum deposit is 5,000") @DecimalMax(value = "5000000", message = "Maximum deposit is 5,000,000") BigDecimal amount,
        @NotNull(message = "payment method is required") PaymentMethod paymentMethod) {

}
