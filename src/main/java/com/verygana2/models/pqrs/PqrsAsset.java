package com.verygana2.models.pqrs;

import java.time.ZonedDateTime;

import org.hibernate.annotations.CreationTimestamp;

import com.verygana2.models.enums.MediaType;
import com.verygana2.models.enums.SupportedMimeType;
import com.verygana2.models.enums.pqrs.PqrsAssetStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Evidencia (foto/video) opcional adjuntada por el solicitante al radicar un
 * PQRS. El asset se sube y se confirma ANTES de que exista el Pqrs (el
 * usuario prepara la evidencia y luego radica el PQRS referenciándola), por
 * eso {@code pqrs} es nullable y la propiedad queda rastreada por
 * {@code ownerUserId} hasta que se "reclama" al crear el Pqrs — ver
 * PqrsAssetServiceImpl.validateAndClaimAssets.
 *
 * Ciclo de vida: PENDING (URL generada, archivo no subido) → VALIDATED
 * (subido y verificado contra R2, pqrs aún null) → VALIDATED con pqrs seteado
 * (reclamado) → ORPHANED/DELETED (limpieza, ver OrphanedAssetsCleanupJob).
 */
@Entity
@Table(name = "pqrs_assets", indexes = {
        @Index(name = "idx_pqrs_assets_pqrs", columnList = "pqrs_id"),
        @Index(name = "idx_pqrs_assets_owner", columnList = "owner_user_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PqrsAsset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_user_id", nullable = false)
    private Long ownerUserId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pqrs_id")
    private Pqrs pqrs;

    @Column(name = "object_key", nullable = false, unique = true, length = 500)
    private String objectKey;

    @Column(name = "original_file_name", length = 255)
    private String originalFileName;

    @Column(name = "size_bytes", nullable = false)
    private Long sizeBytes;

    @Enumerated(EnumType.STRING)
    @Column(name = "mime_type", length = 50)
    private SupportedMimeType mimeType;

    @Enumerated(EnumType.STRING)
    @Column(name = "media_type", length = 20)
    private MediaType mediaType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private PqrsAssetStatus status = PqrsAssetStatus.PENDING;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private ZonedDateTime createdAt;
}
