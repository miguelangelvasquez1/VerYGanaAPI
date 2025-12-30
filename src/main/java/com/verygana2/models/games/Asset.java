package com.verygana2.models.games;

import java.time.ZonedDateTime;
import com.verygana2.models.enums.AssetStatus;
import com.verygana2.models.enums.AssetType;
import com.verygana2.models.enums.MediaType;
import com.verygana2.models.enums.SupportedMimeType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "assets")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Asset {

    @Id
    @GeneratedValue (strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "object_key", nullable = false) //URL en el CDN, oraciones, colores, etc.
    private String objectKey;

    @Column(name = "size_bytes", nullable = false)
    private Long sizeBytes;

    @Enumerated(EnumType.STRING)
    @Column(name = "asset_type", nullable = false)
    private AssetType assetType; // Identificador del asset
        
    @Enumerated(EnumType.STRING)
    @Column(name = "media_type", nullable = false)
    private MediaType mediaType;

    @Enumerated(EnumType.STRING)
    @Column(name = "mime_type", nullable = true)
    private SupportedMimeType mimeType;

    // @Column(name = "resolution", nullable = false) Desde 640 x 360 px hasta Full HD (1920 x 1080)
    // private long resolution;

    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    private AssetStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campaign_id", nullable = true)
    private Campaign campaign;

    @ManyToOne(optional = false)
    @JoinColumn(name = "asset_definition_id")
    private GameAssetDefinition assetDefinition;

    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = ZonedDateTime.now();
        }  
        if (this.status == null) {
            this.status = AssetStatus.PENDING;
        }           
    }
}
