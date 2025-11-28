package com.verygana2.dtos.wallet.responses;

import java.math.BigDecimal;
import java.time.ZonedDateTime;


public record WalletResponse (
    BigDecimal balance,
    BigDecimal blockedBalance,
    ZonedDateTime lastUpDateTime
){
    
}
