package com.verygana2.models.ads;

import java.time.ZonedDateTime;

import com.verygana2.models.enums.AssetStatus;
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
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "ad_assets")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdAsset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Bloqueo optimista. El job de limpieza ({@code OrphanedAssetsCleanupJob}) y
     * {@code AdServiceImpl.createAdWithAsset} / {@code analyzeAsset} pueden escribir
     * la misma fila a la vez: si el job orfana el asset mientras se crea el anuncio,
     * el commit perdedor falla con {@code ObjectOptimisticLockingFailureException}
     * (transitoria → la reintenta {@code @RetryOnConcurrencyConflict}) en lugar de
     * dejar un anuncio apuntando a un asset condenado.
     */
    @Version
    @Column(nullable = false)
    private Long version;

    @Column(nullable = false, unique = true, length = 500)
    private String objectKey;

    @Column(nullable = false)
    private Long sizeBytes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MediaType mediaType;

    @Enumerated(EnumType.STRING)
    @Column(length = 50)
    private SupportedMimeType mimeType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AssetStatus status;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    /**
     * Mínimo por like (céntimos) cotizado al anunciante en el análisis.
     * Se congela aquí para que la re-validación en {@code POST /ads} use el mismo
     * valor aunque un admin cambie {@code AD_COST_PER_SECOND_CENTS} entretanto.
     * {@code null} en assets analizados antes de introducir este campo.
     */
    @Column(name = "min_price_per_like_cents")
    private Long minPricePerLikeCents;

    @ManyToOne(fetch = FetchType.LAZY) //Por si en el futuro se quiere adjuntar varias imagenes a un anuncio.
    @JoinColumn(name = "ad_id")
    private Ad ad; 

    @Column(nullable = false)
    private ZonedDateTime uploadedAt;

    @PrePersist
    protected void onCreate() {
        if (uploadedAt == null) {
            uploadedAt = ZonedDateTime.now();
        }
        if (status == null) {
            status = AssetStatus.PENDING;
        }
    }

    /**
     * Prefijo de {@code objectKey} bajo el que se guardan todos los assets de un
     * anunciante (ver {@code AdServiceImpl#generateAdAssetObjectKey}). Mientras el
     * asset no está vinculado a un {@code Ad} es la única señal de propiedad.
     */
    public static String objectKeyPrefixFor(Long commercialId) {
        return "ads/commercial-" + commercialId + "/";
    }

    /**
     * ¿El asset pertenece a este anunciante? Antes de vincularse a un anuncio
     * ({@code ad == null}) la propiedad se deduce del prefijo del {@code objectKey};
     * después, del anunciante dueño del {@code Ad}. Sin esta comprobación un
     * anunciante autenticado podría analizar u orfanar el asset en subida de otro
     * conociendo su id (IDOR / DoS sobre el flujo de subida ajeno).
     */
    public boolean isOwnedBy(Long commercialId) {
        if (commercialId == null) {
            return false;
        }
        if (ad != null) {
            return ad.getCommercial() != null && commercialId.equals(ad.getCommercial().getId());
        }
        return objectKey != null && objectKey.startsWith(objectKeyPrefixFor(commercialId));
    }
}