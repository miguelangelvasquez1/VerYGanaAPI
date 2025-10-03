package com.VerYGana.dtos2.wallet2.responses2;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TransactionResponse (
    String message,
    BigDecimal amount,
    String referenceId,
    LocalDateTime timeStamp
){
    
}
