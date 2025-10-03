package com.VerYGana.dtos.Wallet.responses;

import java.math.BigDecimal;

public record WalletCreateResponse (
    BigDecimal balance,
    BigDecimal blockedBalance
){
    
}
