package com.verygana2.dtos.treasury;

import java.time.ZonedDateTime;
import java.util.UUID;

public record TreasuryMovementResponseDTO(
        UUID id,
        String fromAccountCode,
        String toAccountCode,
        long amountCents,
        String concept,
        UUID referenceId,
        String referenceType,
        ZonedDateTime createdAt) {
}
