package com.verygana2.models.games;

import java.time.ZonedDateTime;

import org.springframework.data.annotation.Id;

import jakarta.persistence.Column;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

public class GameConfig { // Un solo valor activo
    
    @Id
    @GeneratedValue
    private Long id;

    @ManyToOne(optional = false)
    private GameConfigDefinition definition;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campaign_id", nullable = true)
    private Campaign campaign;

    @Column(name = "numeric_value")
    private Double numericValue;

    @Column(name = "string_value")
    private String stringValue;

    @Column(name = "boolean_value")
    private Boolean booleanValue;

    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;
}
