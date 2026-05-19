package com.verygana2.models.records;

/**
     * Snapshot inmutable de los 4 saldos de tesorería.
     * Usado para el endpoint de auditoría.
     */
    public record TreasurySnapshot(
            long keysReserveCents,
            long fortificationCents,
            long operationsCents,
            long payoutsPendingCents,
            long totalCents
    ) {}