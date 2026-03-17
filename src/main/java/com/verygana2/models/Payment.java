package com.verygana2.models;

import java.time.ZonedDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.verygana2.models.enums.wompi.PaymentStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// src/main/java/com/tuapp/wompi/Payment.java
@Entity
@Table(name = "payments")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String reference;         // Tu referencia única

    @Column(nullable = false)
    private Long amountInCents;       // Monto en centavos (ej: 50000 COP = 5000000)

    @Column(nullable = false)
    private String currency;          // "COP"

    @Column
    private String wompiTransactionId; // ID que devuelve Wompi

    @Enumerated(EnumType.STRING)
    private PaymentStatus status;     // PENDING, APPROVED, DECLINED, VOIDED, ERROR

    @Column(nullable = false)
    private Long userId;              // FK al usuario autenticado

    @Column
    private String userEmail;

    @CreationTimestamp
    private ZonedDateTime createdAt;

    @UpdateTimestamp
    private ZonedDateTime updatedAt;
}
