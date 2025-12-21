package com.verygana2.models.games;

import com.verygana2.models.enums.AssetType;
import com.verygana2.models.enums.MediaType;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "game_asset_definitions", indexes = {
    @Index(name = "idx_game_asset_game", columnList = "game_id"),
    @Index(name = "idx_game_asset_type", columnList = "asset_type")
})
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class GameAssetDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Juego al que pertenece esta definición */
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "game_id", nullable = false)
    private Game game;

    /** Tipo lógico del asset */
    @Enumerated(EnumType.STRING)
    @Column(name = "asset_type", nullable = false)
    private AssetType assetType;

    /** Tipo de media permitido */
    @Enumerated(EnumType.STRING)
    @Column(name = "media_type", nullable = false)
    private MediaType mediaType;

    /** ¿Es obligatorio para lanzar una campaña? */
    @Column(name = "required", nullable = false)
    private boolean required;

    /** ¿Se permiten múltiples assets de este tipo? */
    @Column(name = "multiple", nullable = false)
    private boolean multiple;

    /** Descripción funcional para frontend / advertiser */
    @Column(name = "description")
    private String description;
}
