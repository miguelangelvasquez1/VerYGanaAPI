package com.verygana2.models.games;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "games", indexes = {
    @Index(name = "idx_game_active", columnList = "active")
})
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Game {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description", nullable = false)
    private String description;

    @Column(name = "url", nullable = false)
    private String url;

    @Column(name = "front_page_url", nullable = false)
    private String frontPageUrl;    

    @Column(name = "min_duration_seconds", nullable = false)
    private Integer minDurationSeconds; // Optional

    @Column(name = "max_duration_seconds", nullable = false)
    private Integer maxDurationSeconds; // Optional

    @Column(name = "active", nullable = false)
    private boolean active;

    @OneToMany(mappedBy = "game", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<GameSession> gameSessions;

    @OneToMany(mappedBy = "game", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<GameMetricDefinition> metricDefinitions = new ArrayList<>();
    
    @OneToMany(mappedBy = "game", cascade = CascadeType.ALL)
    @Builder.Default
    private List<Campaign> campaigns = new ArrayList<>();

    @OneToMany(mappedBy = "game", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<GameAssetDefinition> assetDefinitions = new ArrayList<>();

}
