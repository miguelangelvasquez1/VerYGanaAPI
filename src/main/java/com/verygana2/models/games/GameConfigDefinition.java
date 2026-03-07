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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "game_config_definitions")
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
    
    @PrePersist
    protected void onCreate() {
        createdAt = ZonedDateTime.now();
    }

    // @Column(name = "block_key", nullable = false)
    // private String blockKey; // root, branding

    // @Column(name = "json_key", nullable = false)
    // private String jsonKey; // colors, rewards, texts, etc

    // @Column(name = "config_schema", columnDefinition = "json", nullable = false)
    // private String schema; // json de los nodos hijos con su tipo de dato

    // @Column(name = "required", nullable = false)
    // private boolean required;

    // @Column(name = "description")
    // private String description;
}
