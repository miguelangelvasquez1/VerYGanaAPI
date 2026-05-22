package com.verygana2.dtos.treasury;

public record TreasuryBalanceResponseDTO(
        long keysReserveCents,
        long fortificationCents,
        long operationsCents,
        long payoutsPendingCents,
        long totalCents,
        double keysReserveHealthPct,
        /** OK | WARNING | CRITICAL según los umbrales de TreasuryConfig */
        String keysReserveStatus,
        boolean hasNegativeBalance) {
}
