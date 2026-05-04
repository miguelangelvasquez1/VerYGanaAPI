package com.verygana2.dtos.wallet.responses;

import java.time.ZonedDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class WalletResponse {
    private Long availableBalanceCents;
    private ZonedDateTime lastUpdatedAt;
}
