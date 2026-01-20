package com.verygana2.dtos.transaction.responses;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

import com.verygana2.models.enums.TransactionState;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TransactionPayoutResponseDTO {
    private Long id;
    private String referenceId;
    private ZonedDateTime createdAt;
    private BigDecimal amount;
    private TransactionState transactionState;
}
