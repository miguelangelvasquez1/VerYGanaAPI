package com.verygana2.models.games;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class GameConfigDefinition {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Game game;

    @Column(name = "block_key", nullable = false)
    private String blockKey;

    /** branding, colors, audio, texts */
    @Column(name = "json_key", nullable = false)
    private String jsonKey; // colors, audio, etc

    @Column(name = "config_schema", columnDefinition = "json", nullable = false)
    private String schema;

    @Column(name = "required", nullable = false)
    private boolean required;

    @Column(name = "description")
    private String description;
}
