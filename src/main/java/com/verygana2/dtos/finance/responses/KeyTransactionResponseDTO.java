package com.verygana2.dtos.finance.responses;

import java.time.ZonedDateTime;
import java.util.UUID;

import com.verygana2.models.enums.finance.KeyOriginType;
import com.verygana2.models.enums.finance.KeyTransactionType;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class KeyTransactionResponseDTO {

        private UUID id;
        private KeyTransactionType type;
        private KeyOriginType originType;
        /** Cambio del saldo disponible. Null si el movimiento no lo toca (confirmación de copago). */
        private Long purchaseKeysDelta;
        private Long connectivityKeysDelta;
        /** Cambio del saldo bloqueado por un copago en curso. Null si el movimiento no lo toca. */
        private Long blockedPurchaseKeysDelta;
        /** Saldo disponible tras el movimiento. Null en movimientos anteriores al Libro Mayor. */
        private Long purchaseKeysBalanceAfter;
        private Long connectivityKeysBalanceAfter;
        private String reason;
        private UUID referenceId;
        private ZonedDateTime expiredAt;
        private ZonedDateTime createdAt;

}
