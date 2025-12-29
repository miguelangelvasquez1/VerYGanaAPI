package com.verygana2.dtos.transaction.responses;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

import com.verygana2.models.enums.PaymentMethod;
import com.verygana2.models.enums.TransactionState;
import com.verygana2.models.enums.TransactionType;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TransactionResponseDTO {
    private Long id;
    private BigDecimal amount;
    private ZonedDateTime createdAt;
    private PaymentMethod paymentMethod;
    private String referenceId;
    private TransactionType transactionType;
    private TransactionState transactionState;
}
