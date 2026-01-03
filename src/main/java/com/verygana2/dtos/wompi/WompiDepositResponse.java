package com.verygana2.dtos.wompi;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

import com.verygana2.models.enums.TransactionState;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class WompiDepositResponse {
    private Long transactionId;
    private String referenceId;
    private String wompiTransactionId;
    private BigDecimal amount;
    private TransactionState status;
    private String paymentUrl; // Para PSE
    private String message;
    private ZonedDateTime createdAt;
}
