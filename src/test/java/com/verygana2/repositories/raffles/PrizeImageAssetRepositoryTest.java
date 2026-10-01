package com.verygana2.repositories.raffles;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import com.verygana2.models.enums.AssetStatus;
import com.verygana2.models.raffles.PrizeImageAsset;

import jakarta.persistence.EntityManager;

/**
 * Tests de integración H2 (modo MySQL) para PrizeImageAssetRepository.
 */
@DataJpaTest(properties = {
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:prize-image-asset-repo-it;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("PrizeImageAssetRepository (integración H2)")
class PrizeImageAssetRepositoryTest {

    @Autowired
    private EntityManager em;

    @Autowired
    private PrizeImageAssetRepository prizeImageAssetRepository;

    // ==================== HELPERS ====================

    private static ZonedDateTime now() {
        return ZonedDateTime.now();
    }

    /**
     * Persiste el asset con objectKey/sizeBytes fijos y status/uploadedAt
     * explícitos. Como ambos van no-nulos, el @PrePersist del entity (que
     * solo aplica defaults cuando el campo es null) no los sobrescribe.
     */
    private PrizeImageAsset persistAsset(String objectKey, AssetStatus status, ZonedDateTime uploadedAt) {
        PrizeImageAsset asset = new PrizeImageAsset();
        asset.setObjectKey(objectKey);
        asset.setSizeBytes(1024L);
        asset.setStatus(status);
        asset.setUploadedAt(uploadedAt);
        em.persist(asset);
        em.flush();
        return asset;
    }

    // ==================== findDeletableAssets ====================

    @Nested
    @DisplayName("findDeletableAssets")
    class FindDeletableAssets {

        @Test
        @DisplayName("trae solo los assets del status buscado con uploadedAt antes del threshold")
        void returnsOnlyMatchingStatusBeforeThreshold() {
            ZonedDateTime threshold = now();

            PrizeImageAsset deletable = persistAsset("prize/deletable.png", AssetStatus.ORPHANED,
                    threshold.minusDays(1));
            PrizeImageAsset wrongStatus = persistAsset("prize/wrong-status.png", AssetStatus.VALIDATED,
                    threshold.minusDays(1));
            PrizeImageAsset tooRecent = persistAsset("prize/too-recent.png", AssetStatus.ORPHANED,
                    threshold.plusDays(1));

            List<PrizeImageAsset> found = prizeImageAssetRepository.findDeletableAssets(AssetStatus.ORPHANED,
                    threshold);

            assertThat(found).extracting(PrizeImageAsset::getId).containsExactly(deletable.getId());
            assertThat(wrongStatus).isNotNull();
            assertThat(tooRecent).isNotNull();
        }

        @Test
        @DisplayName("retorna lista vacía si nada coincide")
        void returnsEmptyWhenNoMatch() {
            persistAsset("prize/no-match.png", AssetStatus.VALIDATED, now().minusDays(1));

            List<PrizeImageAsset> found = prizeImageAssetRepository.findDeletableAssets(AssetStatus.ORPHANED, now());

            assertThat(found).isEmpty();
        }
    }
}
