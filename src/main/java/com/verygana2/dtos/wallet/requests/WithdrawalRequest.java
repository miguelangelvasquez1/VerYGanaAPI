package com.verygana2.dtos.wallet.requests;

import java.math.BigDecimal;

import com.verygana2.models.PaymentMethod;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public record WithdrawalRequest(
                @NotNull(message = "The amount cannot be null") @DecimalMin(value = "0.01", inclusive = false, message = "The amount must be greater than zero") BigDecimal amount,
                @NotNull(message = "payment method is required") PaymentMethod paymentMethod) {

}
