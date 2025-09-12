package com.VerYGana.dtos.Wallet.Responses;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

public record BalanceResponse (
    BigDecimal balance,
    ZonedDateTime timeStamp
){
    
}
