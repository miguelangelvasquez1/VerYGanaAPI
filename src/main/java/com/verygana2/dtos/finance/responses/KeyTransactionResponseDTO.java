package com.verygana2.dtos.finance.responses;

import java.time.ZonedDateTime;
import java.util.UUID;

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
        private Long purchaseKeysDelta;
        private Long connectivityKeysDelta;
        private String reason;
        private UUID referenceId;
        private ZonedDateTime expiresAt;
        private ZonedDateTime createdAt;

}
