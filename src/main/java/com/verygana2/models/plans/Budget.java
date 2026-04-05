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
@Table(name = "budgets")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Budget {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    private CommercialDetails commercial;

    private BigDecimal totalAmount;
    private BigDecimal remainingAmount;

    private BigDecimal allocatedToAds;
    private BigDecimal allocatedToGames;

    private boolean active;

    private ZonedDateTime createdAt;
}