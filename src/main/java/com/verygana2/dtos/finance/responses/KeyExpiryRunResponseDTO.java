package com.verygana2.dtos.finance.responses;

import java.time.ZonedDateTime;

/** Resultado de una corrida del vencimiento de llaves. */
public record KeyExpiryRunResponseDTO(
        /** Instante con el que se evaluó qué lotes estaban vencidos. */
        ZonedDateTime evaluatedAt,
        /** true si se usó una fecha distinta a la actual (solo permitido fuera de producción). */
        boolean simulated,
        int walletsWithDueLots,
        int walletsFailed,
        /** Centavos de llaves extinguidos; su respaldo pasó de KEYS_RESERVE a FORTIFICATION. */
        long expiredCents) {
}
