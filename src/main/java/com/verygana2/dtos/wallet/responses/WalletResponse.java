package com.verygana2.dtos.wallet.responses;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class WalletResponse {
    private BigDecimal balance;
    private BigDecimal blockedBalance;
    private ZonedDateTime lastUpDateTime;
}
