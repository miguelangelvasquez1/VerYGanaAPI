package com.verygana2.storage.service;

import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.verygana2.models.enums.AssetStatus;
import com.verygana2.models.raffles.PrizeImageAsset;
import com.verygana2.models.raffles.RaffleImageAsset;
import com.verygana2.repositories.AdAssetRepository;
import com.verygana2.repositories.StoryMediaAssetRepository;
import com.verygana2.repositories.games.AssetRepository;
import com.verygana2.repositories.marketplace.ProductCategoryImageAssetRepository;
import com.verygana2.repositories.marketplace.ProductImageAssetRepository;
import com.verygana2.repositories.pqrs.PqrsAssetRepository;
import com.verygana2.repositories.raffles.PrizeImageAssetRepository;
import com.verygana2.repositories.raffles.RaffleImageAssetRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Cubre el hallazgo de auditoria 1.4: los assets de rifas/premios marcados
 * ORPHANED en RaffleServiceImpl no tenian ningun job que los borrara de R2.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OrphanedAssetsCleanupJob (raffle/prize assets)")
class OrphanedAssetsCleanupJobTest {

    @Mock private AdAssetRepository adAssetRepository;
    @Mock private AssetRepository assetRepository;
    @Mock private StoryMediaAssetRepository storyMediaAssetRepository;
    @Mock private ProductImageAssetRepository productImageAssetRepository;
    @Mock private ProductCategoryImageAssetRepository productCategoryImageAssetRepository;
    @Mock private PqrsAssetRepository pqrsAssetRepository;
    @Mock private RaffleImageAssetRepository raffleImageAssetRepository;
    @Mock private PrizeImageAssetRepository prizeImageAssetRepository;
    @Mock private R2Service r2Service;

    private OrphanedAssetsCleanupJob job;

    private OrphanedAssetsCleanupJob newJob() {
        OrphanedAssetsCleanupJob job = new OrphanedAssetsCleanupJob(adAssetRepository, assetRepository,
                storyMediaAssetRepository, productImageAssetRepository, productCategoryImageAssetRepository,
                pqrsAssetRepository, raffleImageAssetRepository, prizeImageAssetRepository, r2Service);
        ReflectionTestUtils.setField(job, "maxAgeHours", 24);
        return job;
    }

    @Test
    @DisplayName("cleanupRaffleImageAssets: borra de R2 y marca DELETED los assets ORPHANED vencidos")
    void cleanupRaffleImageAssets_deletesOrphanedAssets() {
        job = newJob();

        RaffleImageAsset asset = RaffleImageAsset.builder()
                .id(1L)
                .objectKey("raffles/foo.jpg")
                .status(AssetStatus.ORPHANED)
                .uploadedAt(ZonedDateTime.now().minusDays(2))
                .build();
        when(raffleImageAssetRepository.findDeletableAssets(eq(AssetStatus.ORPHANED), any()))
                .thenReturn(List.of(asset));

        job.cleanupRaffleImageAssets();

        verify(r2Service).deleteObject("public/raffles/foo.jpg");
        verify(raffleImageAssetRepository).save(asset);
        assertThat(asset.getStatus()).isEqualTo(AssetStatus.DELETED);
    }

    @Test
    @DisplayName("cleanupRaffleImageAssets: si falla el borrado en R2, no marca el asset como DELETED")
    void cleanupRaffleImageAssets_r2Failure_keepsAssetOrphaned() {
        job = newJob();

        RaffleImageAsset asset = RaffleImageAsset.builder()
                .id(1L)
                .objectKey("raffles/foo.jpg")
                .status(AssetStatus.ORPHANED)
                .uploadedAt(ZonedDateTime.now().minusDays(2))
                .build();
        when(raffleImageAssetRepository.findDeletableAssets(eq(AssetStatus.ORPHANED), any()))
                .thenReturn(List.of(asset));
        org.mockito.Mockito.doThrow(new RuntimeException("R2 down")).when(r2Service).deleteObject(any());

        job.cleanupRaffleImageAssets();

        verify(raffleImageAssetRepository, org.mockito.Mockito.never()).save(any());
        assertThat(asset.getStatus()).isEqualTo(AssetStatus.ORPHANED);
    }

    @Test
    @DisplayName("cleanupPrizeImageAssets: borra de R2 y marca DELETED los assets ORPHANED vencidos")
    void cleanupPrizeImageAssets_deletesOrphanedAssets() {
        job = newJob();

        PrizeImageAsset asset = PrizeImageAsset.builder()
                .id(2L)
                .objectKey("prizes/raffle-1/foo.jpg")
                .status(AssetStatus.ORPHANED)
                .uploadedAt(ZonedDateTime.now().minusDays(2))
                .build();
        when(prizeImageAssetRepository.findDeletableAssets(eq(AssetStatus.ORPHANED), any()))
                .thenReturn(List.of(asset));

        job.cleanupPrizeImageAssets();

        verify(r2Service).deleteObject("public/prizes/raffle-1/foo.jpg");
        verify(prizeImageAssetRepository).save(asset);
        assertThat(asset.getStatus()).isEqualTo(AssetStatus.DELETED);
    }
}
