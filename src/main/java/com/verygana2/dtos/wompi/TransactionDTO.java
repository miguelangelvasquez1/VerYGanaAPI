package com.verygana2.dtos.wompi;

import lombok.Data;

@Data
public class TransactionDTO {
    private String id;
    private String reference;
    private String status;
    private Long amountInCents;
}
