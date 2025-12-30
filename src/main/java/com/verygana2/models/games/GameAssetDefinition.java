package com.verygana2.models.games;

import java.util.Set;

import com.verygana2.models.enums.AssetType;
import com.verygana2.models.enums.MediaType;
import com.verygana2.models.enums.SupportedMimeType;

import jakarta.persistence.*;
import jakarta.validation.ValidationException;
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

    @Column(name = "max_size_bytes", nullable = false)
    private long maxSizeBytes;

    // @Column(name = "resolution", nullable = false)
    // private long resolution, FRECUENCIA; min, max?

    // private int requiredWidth;
    // private int requiredHeight;

    /** ¿Es obligatorio para lanzar una campaña? */
    @Column(name = "required", nullable = false)
    private boolean required;

    /** ¿Se permiten múltiples assets de este tipo? */
    @Column(name = "multiple", nullable = false)
    private boolean multiple;

    /** Descripción funcional para frontend / advertiser */
    @Column(name = "description")
    private String description;

    @ElementCollection(targetClass = SupportedMimeType.class)
    @CollectionTable(
        name = "asset_definition_mime_types",
        joinColumns = @JoinColumn(name = "asset_definition_id")
    )
    @Enumerated(EnumType.STRING)
    @Column(name = "mime_type")
    private Set<SupportedMimeType> allowedMimeTypes;

    @PrePersist
    @PreUpdate
    private void validateBeforeSave() {
        validateDefinition();
    }

    // Métodos Helper

    public void validateMimeType(String contentType) {
        
        if (contentType == null || contentType.isBlank()) {
            throw new ValidationException("Content-Type requerido");
        }

        SupportedMimeType detected = SupportedMimeType.fromValue(contentType);

        if (!allowedMimeTypes.contains(detected)) {
            throw new ValidationException(
                String.format("MimeType %s no permitido. Permitidos: %s", contentType, allowedMimeTypes)
            );
        }
    }

    public void validateDefinition() {

        if (allowedMimeTypes == null || allowedMimeTypes.isEmpty()) {
            throw new ValidationException(
                "Debe definirse al menos un mimeType permitido"
            );
        }

        for (SupportedMimeType mime : allowedMimeTypes) {
            if (mime.getMediaType() != this.mediaType) {
                throw new ValidationException(
                    String.format(
                        "MimeType %s no corresponde al MediaType %s",
                        mime, mediaType
                    )
                );
            }
        }
    }
}