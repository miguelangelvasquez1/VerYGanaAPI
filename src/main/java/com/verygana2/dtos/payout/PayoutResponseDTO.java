package com.verygana2.dtos.payout;

import java.time.ZonedDateTime;
import java.util.UUID;

import com.verygana2.models.enums.finance.PayoutStatus;

public record PayoutResponseDTO(
        UUID id,
        Long commercialId,
        String companyName,
        Long grossAmountCents,
        Long commissionAmountCents,
        Long netAmountCents,
        Integer commissionPctApplied,
        PayoutStatus status,
        ZonedDateTime scheduledAt,
        ZonedDateTime paidAt,
        ZonedDateTime periodStart,
        ZonedDateTime periodEnd,
        String failureReason,
        Integer retryCount) {
}
