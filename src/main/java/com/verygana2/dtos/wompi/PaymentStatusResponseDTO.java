package com.verygana2.dtos.wompi;

import java.time.ZonedDateTime;

import com.verygana2.models.enums.wompi.PaymentStatus;

import lombok.Builder;
import lombok.Data;

@Data 
@Builder
public class PaymentStatusResponseDTO {
    private String reference;
    private PaymentStatus status;
    private Long amountInCents;
    private String wompiTransactionId;
    private ZonedDateTime createdAt;
}
