package com.verygana2.storage.service;

import java.time.ZonedDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.verygana2.models.ImpactStory.StoryMediaAsset;
import com.verygana2.models.ads.AdAsset;
import com.verygana2.models.enums.AssetStatus;
import com.verygana2.models.games.Asset;
import com.verygana2.repositories.AdAssetRepository;
import com.verygana2.repositories.StoryMediaAssetRepository;
import com.verygana2.repositories.games.AssetRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Scheduled job to clean up orphaned assets in R2.
 * Runs automatically to free up storage space.
 */
@Component
@ConditionalOnProperty(
    prefix = "cleanup.orphaned-assets",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = false
)
@Slf4j
@RequiredArgsConstructor
public class OrphanedAssetsCleanupJob {

    private final AdAssetRepository adAssetRepository;
    private final AssetRepository assetRepository;
    private final StoryMediaAssetRepository storyMediaAssetRepository;
    private final R2Service r2Service;

    @Value("${cleanup.orphaned-assets.max-age-hours:24}")
    private int maxAgeHours; // Configurable: how long to wait before deleting orphaned assets

    // For Ads
    @Transactional
    @Scheduled(cron = "0 0 * * * *") // every hour
    public void cleanupAdAssets() {

        ZonedDateTime threshold = ZonedDateTime.now().minusHours(maxAgeHours);

        List<AdAsset> assets = adAssetRepository.findDeletableAssets(AssetStatus.ORPHANED, threshold);
        log.info("Cleanup job: {} candidate ad assets", assets.size());

        for (AdAsset asset : assets) {
            try {
                r2Service.deleteObject("private/" + asset.getObjectKey());
                asset.setStatus(AssetStatus.DELETED);
                adAssetRepository.save(asset);

                log.info("Ad Asset {} deleted from R2", asset.getId());

            } catch (Exception e) {
                log.warn("Failed to delete asset {} ({}): {}", asset.getId(), asset.getObjectKey(), e.getMessage());
            }
        }
    }

    // For Impact Stories
    @Transactional
    @Scheduled(cron = "0 0 * * * *") // every hour
    public void cleanupImpactStoriesAssets() {

        ZonedDateTime threshold = ZonedDateTime.now().minusHours(maxAgeHours);

        List<StoryMediaAsset> assets = storyMediaAssetRepository.findDeletableAssets( StoryMediaAsset.MediaAssetStatus.ORPHANED, threshold);
        log.info("Cleanup job: {} candidate impact story assets", assets.size());

        for (StoryMediaAsset asset : assets) {
            try {
                r2Service.deleteObject(asset.getObjectKey());
                asset.setStatus(StoryMediaAsset.MediaAssetStatus.DELETED);
                storyMediaAssetRepository.save(asset);

                log.info("Impact Story Asset {} deleted from R2", asset.getId());

            } catch (Exception e) {
                log.warn("Failed to delete asset {} ({}): {}", asset.getId(), asset.getObjectKey(), e.getMessage());
            }
        }
    }

    // For campaigns
    @Transactional //    @Scheduled(cron = "${cleanup.orphaned-assets.cron:0 0 2 * * ?}")
    @Scheduled(cron = "0 0 * * * *") // every hour
    public void cleanupAssets() {

        ZonedDateTime threshold = ZonedDateTime.now().minusHours(maxAgeHours);

        List<Asset> assets = assetRepository.findDeletableAssets(AssetStatus.ORPHANED, threshold); // TODO: also handle assets stuck in PENDING and remove job for ad assets

        log.info("Cleanup job: {} candidate campaign assets", assets.size());

        for (Asset asset : assets) {
            try {
                r2Service.deleteObject(asset.getObjectKey());
                asset.setStatus(AssetStatus.DELETED);
                assetRepository.save(asset);

                log.info("Campaign Asset {} deleted from R2", asset.getId());

            } catch (Exception e) {
                log.warn(
                    "Failed to delete asset {} ({}): {}",
                    asset.getId(),
                    asset.getObjectKey(),
                    e.getMessage()
                );
            }
        }
    }

    /**
     * R2 service health check every 5 minutes.
     */
    @Scheduled(fixedRate = 300000) // 5 minutes
    public void healthCheck() {
        if (!r2Service.healthCheck()) {
            log.error("R2 health check FAILED - Service unavailable");
            // Optional: send critical alert
        }
    }
}