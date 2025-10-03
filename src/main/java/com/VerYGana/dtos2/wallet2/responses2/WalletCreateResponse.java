package com.VerYGana.dtos2.wallet2.responses2;

import java.math.BigDecimal;

public record WalletCreateResponse (
    BigDecimal balance,
    BigDecimal blockedBalance
){
    
}
