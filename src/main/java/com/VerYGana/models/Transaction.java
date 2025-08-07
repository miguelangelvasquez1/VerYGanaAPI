package com.VerYGana.models;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import com.VerYGana.models.Enums.TransactionState;
import com.VerYGana.models.Enums.TransactionType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import lombok.Data;

@Entity
@Data
public class Transaction { //Future consideration: currency column, hacer esto con stripe
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, updatable = false)
    private String referenceId; //For external reference

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne
    private PayoutMethod payoutMethod;

    @Enumerated(EnumType.STRING)
    private TransactionType transactionType;

    @Enumerated(EnumType.STRING)
    private TransactionState transactionState;

    @Column(precision = 15, scale = 2)
    private BigDecimal amount; //igual a 0, pre persist?
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;

    @PrePersist
    public void assignReferenceId() {
        if (this.referenceId == null) {
            this.referenceId = UUID.randomUUID().toString();
        }
    }
}
