package com.Rifacel.models;

import java.time.LocalDateTime;



import com.Rifacel.models.Enums.TransactionState;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class Transaction {
    private String id;
    private String userId;
    private String methodPayment;
    private double amount;
    private LocalDateTime date;
    private String referenceCode;
    private TransactionState state; 
}
