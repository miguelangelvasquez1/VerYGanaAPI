package com.VerYGana.dtos.Wallet.responses;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TransactionResponse (
    String message,
    BigDecimal amount,
    String referenceId,
    LocalDateTime timeStamp
){
    
}
