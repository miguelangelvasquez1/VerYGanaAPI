package com.verygana2.models.games;

import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "games")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Game {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", nullable = false)
    private String code;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "min_duration_seconds", nullable = false)
    private Integer minDurationSeconds; // Optional

    @Column(name = "max_duration_seconds", nullable = false)
    private Integer maxDurationSeconds; // Optional

    @Column(name = "active", nullable = false)
    private boolean active;

    @OneToMany(mappedBy = "game", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<GameSession> gameSessions;

    @OneToMany
    private List<GameMetricDefinition> metricDefinitions;

    // Assets definition?
}
