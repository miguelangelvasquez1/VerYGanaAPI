package com.verygana2.storage.service;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.verygana2.models.ImpactStory.StoryMediaAsset;
import com.verygana2.models.ads.AdAsset;
import com.verygana2.models.enums.AssetStatus;
import com.verygana2.models.finance.PayoutMethodCertificateAsset;
import com.verygana2.models.marketplace.ProductCategoryImageAsset;
import com.verygana2.models.marketplace.ProductImageAsset;
import com.verygana2.models.raffles.PrizeImageAsset;
import com.verygana2.models.raffles.RaffleImageAsset;
import com.verygana2.repositories.AdAssetRepository;
import com.verygana2.repositories.StoryMediaAssetRepository;
import com.verygana2.repositories.branding.CorporateResourceRepository;
import com.verygana2.repositories.finance.PayoutMethodCertificateAssetRepository;
import com.verygana2.repositories.games.AssetRepository;
import com.verygana2.repositories.marketplace.ProductCategoryImageAssetRepository;
import com.verygana2.repositories.marketplace.ProductImageAssetRepository;
import com.verygana2.repositories.raffles.PrizeImageAssetRepository;
import com.verygana2.repositories.raffles.RaffleImageAssetRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
@RequiredArgsConstructor
public class AssetOrphanedService {

    private final AssetRepository assetRepository;
    private final AdAssetRepository adAssetRepository;
    private final StoryMediaAssetRepository storyMediaAssetRepository;
    private final ProductImageAssetRepository productImageAssetRepository;
    private final ProductCategoryImageAssetRepository productCategoryImageAssetRepository;
    private final PayoutMethodCertificateAssetRepository payoutMethodCertificateAssetRepository;
    private final RaffleImageAssetRepository raffleImageAssetRepository;
    private final PrizeImageAssetRepository prizeImageAssetRepository;
    private final CorporateResourceRepository corporateResourceRepository;

    /**
     * Para media de historias de impacto que quedó sin claim (falló la creación de la historia).
     *
     * <p>Solo orfana lo que sigue sin vincular ({@code impactStory == null}): si el llamador
     * reintenta o envía dos veces la misma petición, el segundo intento falla al validar el
     * asset ya reclamado y, sin esta guarda, condenaría al borrado la media de una historia
     * viva. Borrar una historia (media ya vinculada) no pasa por aquí sino por
     * {@code StoryMediaAssetService#markOrphaned}.
     *
     * <p>REQUIRES_NEW: se invoca desde un catch cuya transacción se revierte al relanzar la
     * excepción; en la misma transacción el marcado se perdería y el archivo quedaría en R2.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markImpactStoryAssetsAsOrphanedByIds(Collection<Long> assetIds) {

        List<StoryMediaAsset> assets = storyMediaAssetRepository.findAllById(Objects.requireNonNull(assetIds));
        for (StoryMediaAsset asset : assets) {
            if (asset.getImpactStory() == null
                    && (asset.getStatus() == StoryMediaAsset.MediaAssetStatus.PENDING
                        || asset.getStatus() == StoryMediaAsset.MediaAssetStatus.VALIDATED)) {

                asset.setStatus(StoryMediaAsset.MediaAssetStatus.ORPHANED);
            }
        }
    }

    /**
     * Para la imagen de portada de una rifa cuya confirmación falló. Solo orfana lo que sigue
     * sin vincular a una rifa (ver {@link #markImpactStoryAssetsAsOrphanedByIds} para el porqué
     * de la guarda y de REQUIRES_NEW).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markRaffleImageAssetsAsOrphanedByIds(Collection<Long> assetIds) {

        List<RaffleImageAsset> assets = raffleImageAssetRepository.findAllById(Objects.requireNonNull(assetIds));
        for (RaffleImageAsset asset : assets) {
            if (asset.getRaffle() == null
                    && (asset.getStatus() == AssetStatus.PENDING || asset.getStatus() == AssetStatus.VALIDATED)) {

                asset.setStatus(AssetStatus.ORPHANED);
            }
        }
    }

    /**
     * Para las imágenes de premios de una rifa cuya confirmación falló. Misma guarda que
     * {@link #markRaffleImageAssetsAsOrphanedByIds}.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markPrizeImageAssetsAsOrphanedByIds(Collection<Long> assetIds) {

        List<PrizeImageAsset> assets = prizeImageAssetRepository.findAllById(Objects.requireNonNull(assetIds));
        for (PrizeImageAsset asset : assets) {
            if (asset.getPrize() == null
                    && (asset.getStatus() == AssetStatus.PENDING || asset.getStatus() == AssetStatus.VALIDATED)) {

                asset.setStatus(AssetStatus.ORPHANED);
            }
        }
    }

    /**
     * Para recursos corporativos cuya confirmación de subida falló. A diferencia de los demás
     * assets, un recurso nace ya vinculado a su solicitud de branding, así que la guarda es el
     * estado: solo un PENDING (nunca confirmado) puede orfanarse; uno VALIDATED es un archivo
     * en uso por la solicitud.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markCorporateResourceAsOrphaned(Long resourceId) {

        corporateResourceRepository.findById(Objects.requireNonNull(resourceId)).ifPresent(resource -> {
            if (resource.getStatus() == AssetStatus.PENDING) {
                resource.markAsOrphan();
            }
        });
    }

    /**
     * Para asset de campaigns.
     */

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markAsOrphaned(Long assetId) {
        assetRepository.findById(assetId).ifPresent(asset -> {
            asset.setStatus(AssetStatus.ORPHANED);
            assetRepository.save(asset);
        });
    }

    /**
     * Para ad assets
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markAdAssetsAsOrphanedByIds(Collection<Long> assetIds) {

        List<AdAsset> assets = adAssetRepository.findAllById(Objects.requireNonNull(assetIds));
        for (AdAsset asset : assets) {
            // ANALYZING incluido: si el análisis (R2 / ffprobe) falla, el asset
            // quedó marcado ANALYZING en su propia transacción y hay que soltarlo.
            if (asset.getStatus() == AssetStatus.VALIDATED ||
                asset.getStatus() == AssetStatus.PENDING ||
                asset.getStatus() == AssetStatus.ANALYZING) {

                asset.setStatus(AssetStatus.ORPHANED);
            }
        }
    }

    /**
     * Para product image assets.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markProductImageAssetsAsOrphanedByIds(Collection<Long> assetIds) {

        List<ProductImageAsset> assets = productImageAssetRepository.findAllById(Objects.requireNonNull(assetIds));
        for (ProductImageAsset asset : assets) {
            if (asset.getStatus() == AssetStatus.VALIDATED ||
                asset.getStatus() == AssetStatus.PENDING) {

                asset.setStatus(AssetStatus.ORPHANED);
            }
        }
    }

    /**
     * Para product category image assets.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markProductCategoryImageAssetsAsOrphanedByIds(Collection<Long> assetIds) {

        List<ProductCategoryImageAsset> assets = productCategoryImageAssetRepository
                .findAllById(Objects.requireNonNull(assetIds));
        for (ProductCategoryImageAsset asset : assets) {
            if (asset.getStatus() == AssetStatus.VALIDATED ||
                asset.getStatus() == AssetStatus.PENDING) {

                asset.setStatus(AssetStatus.ORPHANED);
            }
        }
    }

    /**
     * Para certificaciones bancarias de payout methods.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markPayoutMethodCertificateAssetsAsOrphanedByIds(Collection<Long> assetIds) {

        List<PayoutMethodCertificateAsset> assets = payoutMethodCertificateAssetRepository
                .findAllById(Objects.requireNonNull(assetIds));
        for (PayoutMethodCertificateAsset asset : assets) {
            if (asset.getStatus() == AssetStatus.VALIDATED ||
                asset.getStatus() == AssetStatus.PENDING) {

                asset.setStatus(AssetStatus.ORPHANED);
            }
        }
    }
}
