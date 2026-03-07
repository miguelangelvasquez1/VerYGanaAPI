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
import com.verygana2.repositories.AdAssetRepository;
import com.verygana2.repositories.StoryMediaAssetRepository;
import com.verygana2.repositories.games.AssetRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
@RequiredArgsConstructor
public class AssetOrphanedService {
    
    private final AssetRepository assetRepository;
    private final AdAssetRepository adAssetRepository;
    private final StoryMediaAssetRepository storyMediaAssetRepository;

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
}
