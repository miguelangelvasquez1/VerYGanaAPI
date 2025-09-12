package com.VerYGana.dtos.Wallet.Responses;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

public record TransactionResponse (
    String message,
    BigDecimal amount,
    String referenceId,
    ZonedDateTime timeStamp
){
    
}
