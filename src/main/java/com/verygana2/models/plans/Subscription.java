package com.verygana2.models.plans;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

import com.verygana2.models.userDetails.CommercialDetails;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "subscriptions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Subscription {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    private CommercialDetails commercialDetails;

    @ManyToOne
    private Plan plan;

    private SubscriptionStatus status;

    private BigDecimal amount;

    private BigDecimal recoveredAmount; // suma de ventas
    private boolean amountRecovered; // true si recoveredAmount >= amount * 6
    private ZonedDateTime recoveryDate; // fecha de la última venta recuperada

    private ZonedDateTime startDate;
    private ZonedDateTime endDate;

    public enum SubscriptionStatus {
        ACTIVE,
        INACTIVE,
        CANCELLED
    }
}
