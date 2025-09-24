package com.VerYGana.dtos.Wallet.Responses;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TransactionResponse (
    String message,
    BigDecimal amount,
    String referenceId,
    LocalDateTime timeStamp
){
    
}
