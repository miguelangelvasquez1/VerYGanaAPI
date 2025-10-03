package com.VerYGana.dtos2.admin2.responses2;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AdminReportResponse(
    String message,
    BigDecimal balance,
    BigDecimal newBalance,
    LocalDateTime timeStamp
) {
    
}
