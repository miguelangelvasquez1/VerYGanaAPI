package com.verygana2.dtos.wallet.responses;

import java.time.ZonedDateTime;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class KeyWalletResponseDTO {
    private Long purchaseKeysCents;
    private Long blockedPurchaseKeysCents;
    private Long connectivityKeysCents;
    private Long blockedConnectivityKeysCents;
    private ZonedDateTime updatedAt;
}
