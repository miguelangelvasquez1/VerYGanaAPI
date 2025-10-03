package com.VerYGana.dtos.admin.responses;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AdminReportResponse(
    String message,
    BigDecimal balance,
    BigDecimal newBalance,
    LocalDateTime timeStamp
) {
    
}
