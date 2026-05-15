package com.verygana2.dtos.wallet.responses;

import java.time.ZonedDateTime;
import java.util.UUID;

import com.verygana2.models.enums.finance.PayoutStatus;

public record PayoutSummaryResponse(
        UUID id,
        Long grossAmountCents,
        Long commissionCents,
        Long netAmountCents,
        PayoutStatus status,
        ZonedDateTime scheduledAt,
        ZonedDateTime paidAt) {
}
