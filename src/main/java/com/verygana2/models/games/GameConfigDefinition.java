package com.verygana2.models.games;

import com.verygana2.models.enums.GameSettingValueType;

import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;

public class GameConfigDefinition {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Game game;

    @Column(name = "json_key", nullable = false)
    private String jsonKey; // liftForce, gravity, etc.

    @Enumerated(EnumType.STRING)
    @Column(name = "value_type", nullable = false)
    private GameSettingValueType valueType;

    @Column(name = "required", nullable = false)
    private boolean required;

    @Column(name = "min_value")
    private Double minValue;

    @Column(name = "max_value")
    private Double maxValue;

    @Column(name = "description")
    private String description;
}
