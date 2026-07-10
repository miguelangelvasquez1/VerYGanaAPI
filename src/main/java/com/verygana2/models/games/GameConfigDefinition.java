package com.verygana2.models.games;

import java.time.ZonedDateTime;
import java.util.Map;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "game_config_definitions",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_game_version", columnNames = {"game_id", "version"})
    }
)
public class GameConfigDefinition {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "game_id", nullable = false)
    private Game game;

    @Column(nullable = false, length = 50)
    private Long version; // "1.0.0", "1.1.0"

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "json_schema", columnDefinition = "json", nullable = false)
    private Map<String, Object> jsonSchema;

    // Optional UI Schema for frontend hints
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "ui_schema", columnDefinition = "json")
    private Map<String, Object> uiSchema;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;
    
    @Column(name = "is_latest", nullable = false)
    @Builder.Default
    private Boolean isLatest = false;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;
    
    @Column(name = "created_by", length = 100)
    private String createdBy;

    // CONFIG DE RECOMPENSAS (se deben calcular juego a juego)

    @Column(name = "score_reward_factor", nullable = false)
    private Double scoreRewardFactor;

    @Column(name = "completion_reward_cents", nullable = false)
    private Long completionRewardCents;

    @Column(name = "max_reward_per_session_cents", nullable = false)
    private Long maxRewardPerSessionCents;

    @Column(name = "average_reward_per_session_cents", nullable = false)
    private Long averageRewardPerSessionCents;

    @Column(name = "average_duration_seconds", nullable = false)
    private Integer averageDurationSeconds;

    @PrePersist
    protected void onCreate() {
        createdAt = ZonedDateTime.now();
    }
}
