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
    ) {
        /** Porcentaje que representa KEYS_RESERVE sobre el total. Útil para monitorear salud del fondo. */
        public double keysReserveHealthPct() {
            if (totalCents == 0) return 0.0;
            return (keysReserveCents * 100.0) / totalCents;
        }
    }