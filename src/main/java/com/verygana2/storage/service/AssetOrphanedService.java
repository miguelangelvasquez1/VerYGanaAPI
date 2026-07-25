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
import com.verygana2.models.marketplace.ProductCategoryImageAsset;
import com.verygana2.models.marketplace.ProductImageAsset;
import com.verygana2.repositories.AdAssetRepository;
import com.verygana2.repositories.StoryMediaAssetRepository;
import com.verygana2.repositories.games.AssetRepository;
import com.verygana2.repositories.marketplace.ProductCategoryImageAssetRepository;
import com.verygana2.repositories.marketplace.ProductImageAssetRepository;

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

    // Hacer el del foro
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markAsOrphanedImpactStoryAsset(Long assetId) {
        storyMediaAssetRepository.findById(assetId).ifPresent(asset -> {
            asset.setStatus(StoryMediaAsset.MediaAssetStatus.DELETED);
            storyMediaAssetRepository.save(asset);
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
            if (asset.getStatus() == AssetStatus.VALIDATED ||
                asset.getStatus() == AssetStatus.PENDING) {

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
}
