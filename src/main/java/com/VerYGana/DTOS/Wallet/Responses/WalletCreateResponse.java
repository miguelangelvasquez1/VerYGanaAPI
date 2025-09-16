package com.VerYGana.dtos.Wallet.Responses;

import java.math.BigDecimal;

public record WalletCreateResponse (
    BigDecimal balance,
    BigDecimal blockedBalance
){
    
}
