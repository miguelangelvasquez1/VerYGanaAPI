package com.verygana2.dtos.treasury;

public record TreasuryBalanceResponseDTO(
        long keysReserveCents,
        long fortificationCents,
        long operationsCents,
        long payoutsPendingCents,
        long totalCents,
        /** Peso de KEYS_RESERVE sobre el total de tesorería. Mide reparto, no solvencia. */
        double keysReserveHealthPct,
        /** Pasivo de llaves vivas en centavos: lo que KEYS_RESERVE tiene que respaldar. */
        long keyLiabilityCents,
        /**
         * Respaldo real: KEYS_RESERVE / pasivo de llaves × 100. Por debajo de 100
         * hay llaves emitidas sin plata detrás. Es la métrica de solvencia — a
         * diferencia de keysReserveHealthPct, que sube cuando entra dinero a
         * KEYS_RESERVE aunque no respalde nada.
         */
        double keysBackingPct,
        /** OK | WARNING | CRITICAL según los umbrales de TreasuryConfig y el respaldo */
        String keysReserveStatus,
        boolean hasNegativeBalance) {
}
