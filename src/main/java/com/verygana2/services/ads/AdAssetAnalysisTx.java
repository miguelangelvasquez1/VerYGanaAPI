package com.verygana2.services.ads;

import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.verygana2.models.ads.AdAsset;
import com.verygana2.models.enums.AssetStatus;
import com.verygana2.models.enums.MediaType;
import com.verygana2.models.enums.SupportedMimeType;
import com.verygana2.repositories.AdAssetRepository;

import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ValidationException;
import lombok.RequiredArgsConstructor;

/**
 * Fases transaccionales cortas del análisis de un {@code AdAsset}
 * ({@link AdServiceImpl#analyzeAsset}).
 *
 * <p>Se separan del trabajo externo —validación del objeto en R2 y lectura de
 * duración con ffprobe, que pueden tardar segundos— para que ni la transacción
 * ni la conexión del pool queden retenidas durante esas llamadas. Cada fase
 * confirma por su cuenta ({@code REQUIRES_NEW}): si el trabajo externo falla, la
 * marca {@code ANALYZING} ya está persistida y el llamador mueve el asset a
 * {@code ORPHANED} — no hay una transacción larga que revertir ni un lock de
 * fila sostenido que pueda entrar en deadlock con el job de limpieza.
 */
@Service
@RequiredArgsConstructor
public class AdAssetAnalysisTx {

    private final AdAssetRepository adAssetRepository;

    /** Lo que el trabajo externo necesita del asset, ya sin la fila bloqueada. */
    public record AnalysisContext(MediaType mediaType, long sizeBytes, String objectKey,
                                  Integer storedImageDurationSeconds) {}

    /**
     * Fase 1: valida propiedad y estado del asset y lo marca {@code ANALYZING} en
     * su propia transacción. Si dos peticiones entran a la vez, la segunda ve
     * {@code ANALYZING} y falla aquí con {@link ValidationException}.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AnalysisContext begin(Long assetId, Long commercialId) {
        AdAsset asset = adAssetRepository
                .findById(Objects.requireNonNull(assetId))
                .orElseThrow(() -> new EntityNotFoundException("Asset no encontrado: " + assetId));

        // Propiedad: sin esto un anunciante podría lanzar el análisis (ffprobe /
        // validación en R2, y el posible orphaning que sigue a un fallo) sobre el
        // asset PENDING de otro conociendo su id.
        if (!asset.isOwnedBy(commercialId)) {
            throw new EntityNotFoundException("Asset no encontrado: " + assetId);
        }

        if (asset.getAd() != null) {
            throw new ValidationException("Asset ya está vinculado a un anuncio");
        }
        if (asset.getStatus() != AssetStatus.PENDING) {
            throw new ValidationException(
                    "El asset no está en estado válido para analizar. Estado actual: " + asset.getStatus());
        }

        asset.setStatus(AssetStatus.ANALYZING);
        adAssetRepository.save(asset);

        return new AnalysisContext(
                asset.getMediaType(), asset.getSizeBytes(), asset.getObjectKey(), asset.getDurationSeconds());
    }

    /**
     * Fase 3: persiste el resultado del análisis y deja el asset
     * {@code VALIDATED}, en una transacción corta.
     *
     * @param minPricePerLikeCents mínimo por like cotizado al anunciante; se
     *        congela en el asset para re-validarlo tal cual al crear el anuncio.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(Long assetId, SupportedMimeType realMimeType, int durationSeconds, long minPricePerLikeCents) {
        AdAsset asset = adAssetRepository
                .findById(Objects.requireNonNull(assetId))
                .orElseThrow(() -> new EntityNotFoundException("Asset no encontrado: " + assetId));

        asset.setMimeType(realMimeType);
        asset.setDurationSeconds(durationSeconds);
        asset.setMinPricePerLikeCents(minPricePerLikeCents);
        asset.setStatus(AssetStatus.VALIDATED);
        adAssetRepository.save(asset);
    }
}
