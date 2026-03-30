package com.verygana2.models;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "pricing_configs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PricingConfig {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Enumerated(EnumType.STRING)
    private PricingType type;
    private BigDecimal value;
    private String currency;
    private boolean active;
    private ZonedDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = ZonedDateTime.now();
        }
    }

    public enum PricingType {
        SURVEY,
        GAME,
        AD
    }
}
