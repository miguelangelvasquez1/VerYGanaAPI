package com.Rifacel.models;

import java.math.BigDecimal;
import java.time.LocalDateTime;



import com.Rifacel.models.Enums.TransactionState;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Transaction {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    @ManyToOne
    private User user;
    private String methodPayment;
    @Column(precision = 10, scale = 2)
    private BigDecimal amount;
    private LocalDateTime date;
    private String referenceCode;
    @Enumerated(EnumType.STRING)
    private TransactionState state; 
}
