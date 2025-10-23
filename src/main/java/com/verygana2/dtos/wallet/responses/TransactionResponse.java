package com.verygana2.dtos.wallet.responses;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

public record TransactionResponse (
    String message,
    BigDecimal amount,
    String referenceId,
    ZonedDateTime timeStamp
){
    
}
