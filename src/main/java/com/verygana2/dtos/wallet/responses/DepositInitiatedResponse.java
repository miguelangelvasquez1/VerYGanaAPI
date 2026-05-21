package com.verygana2.dtos.wallet.responses;

import java.time.ZonedDateTime;

public record DepositInitiatedResponse(
        String reference,
        String checkoutUrl,
        Long amountCents,
        ZonedDateTime createdAt) {
}
