package com.verygana2.dtos.wallet.responses;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TransactionResponse (
    String message,
    BigDecimal amount,
    String referenceId,
    LocalDateTime timeStamp
){
    
}
