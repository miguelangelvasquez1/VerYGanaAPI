package com.verygana2.models;

import java.time.ZonedDateTime;

import jakarta.persistence.Column;
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

    private Integer version;

    @Enumerated(EnumType.STRING)
    private PricingType type;
    
    @Column(nullable = false)
    private Long amountInCents;
    @Column(nullable = false)
    private String currency;
    @Column(nullable = false)
    private boolean active;
    @Column(nullable = false)
    private String description;
    @Column(nullable = false)
    private ZonedDateTime createdAt;

    @PrePersist
    public void prePersist() {

        if (createdAt == null) {
            createdAt = ZonedDateTime.now();
        }

        if (currency == null) {
            currency = "COP";
        }
    }

    public enum PricingType {
        SURVEY_REWARD_PER_QUESTION_CENTS,
        GAME_COST_PER_POINT_CENTS,
        GAME_COST_PER_VICTORY_CENTS,
        AD_COST_PER_SECOND_CENTS
    }
}
