package com.verygana2.dtos.admin.responses;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AdminReportResponse(
    String message,
    BigDecimal balance,
    BigDecimal newBalance,
    LocalDateTime timeStamp
) {
    
}
