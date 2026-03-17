package com.verygana2.models.ImpactStory;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import com.verygana2.models.enums.MediaType;
import com.verygana2.models.enums.SupportedMimeType;

import java.time.ZonedDateTime;

/**
 * Representa un archivo multimedia asociado a una historia de impacto.
 *
 * Es la única entidad de media del sistema: unifica el ciclo de vida
 * del asset en R2 (PENDING → VALIDATED → ORPHANED) con los metadatos
 * editoriales que define el admin (altText, displayOrder, isCover, fileName).
 *
 * Flujo de estados:
 *   PENDING   → creado cuando el admin solicita la pre-signed URL (antes de subir)
 *   VALIDATED → el archivo fue subido a R2 y asociado a una historia
 *   ORPHANED  → el admin abandonó el proceso; candidato a limpieza
 */
@Entity
@Table(name = "story_media_assets")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StoryMediaAsset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "object_key", nullable = false, unique = true, length = 500)
    private String objectKey;

    /** URL pública del CDN; disponible una vez subido el archivo */
    @Column(name = "public_url", length = 500)
    private String publicUrl;

    @Column(name = "thumbnail_url", length = 500)
    private String thumbnailUrl;

    @Column(name = "size_bytes", nullable = false)
    private Long sizeBytes;

    @Enumerated(EnumType.STRING)
    @Column(name = "mime_type", length = 100)
    private SupportedMimeType mimeType;

    @Enumerated(EnumType.STRING)
    @Column(name = "media_type", nullable = false, length = 20)
    private MediaType mediaType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private MediaAssetStatus status = MediaAssetStatus.PENDING;

    // ── Metadatos editoriales (definidos por el admin en el formulario) ────────

    /** Nombre original del archivo tal como lo subió el admin */
    @Column(name = "file_name", length = 255)
    private String fileName;

    /** Texto alternativo para accesibilidad */
    @Column(name = "alt_text", length = 255)
    private String altText;

    /** Posición en la galería (0-based) */
    @Column(name = "display_order")
    @Builder.Default
    private Integer displayOrder = 0;

    /** Si true, este archivo se usa como imagen de portada de la historia */
    @Column(name = "is_cover")
    @Builder.Default
    private Boolean isCover = false;

    // ── Relación con la historia ──────────────────────────────────────────────

    /**
     * Historia a la que pertenece este asset.
     * Null mientras está en estado PENDING (aún no se ha creado la historia).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "impact_story_id")
    private ImpactStory impactStory;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private ZonedDateTime createdAt;

    // ── Enum de estado ────────────────────────────────────────────────────────

    public enum MediaAssetStatus {
        PENDING,    // pre-signed URL solicitada, archivo aún no subido
        VALIDATED,  // archivo subido y asociado a una historia
        ORPHANED,    // proceso abandonado; pendiente de limpieza en R2
        DELETED // eliminado de R2
    }
}