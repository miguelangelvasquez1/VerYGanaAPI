package com.verygana2.repositories.marketplace;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import com.verygana2.models.enums.AssetStatus;
import com.verygana2.models.marketplace.ProductCategory;
import com.verygana2.models.marketplace.ProductCategoryImageAsset;

import jakarta.persistence.EntityManager;

/**
 * Tests de integración H2 (modo MySQL) para ProductCategoryImageAssetRepository.
 */
@DataJpaTest(properties = {
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:product-category-image-asset-repo-it;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("ProductCategoryImageAssetRepository (integración H2)")
class ProductCategoryImageAssetRepositoryTest {

    @Autowired
    private EntityManager em;

    @Autowired
    private ProductCategoryImageAssetRepository productCategoryImageAssetRepository;

    private static final AtomicLong SEQ = new AtomicLong(1);

    // ==================== HELPERS ====================

    private static ZonedDateTime now() {
        return ZonedDateTime.now();
    }

    private ProductCategory persistCategory() {
        long n = SEQ.getAndIncrement();
        ProductCategory category = new ProductCategory();
        category.setName("categoria-cat-img-" + n);
        em.persist(category);
        em.flush();
        return category;
    }

    /**
     * Persiste el asset con objectKey/sizeBytes fijos y status/uploadedAt
     * explícitos (ambos no-nulos, así que el @PrePersist del entity, que solo
     * aplica defaults cuando el campo es null, no los sobrescribe).
     */
    private ProductCategoryImageAsset persistAsset(ProductCategory category, String objectKey, AssetStatus status,
            ZonedDateTime uploadedAt) {
        ProductCategoryImageAsset asset = new ProductCategoryImageAsset();
        asset.setObjectKey(objectKey);
        asset.setSizeBytes(1536L);
        asset.setStatus(status);
        asset.setUploadedAt(uploadedAt);
        asset.setProductCategory(category);
        em.persist(asset);
        em.flush();
        return asset;
    }

    private static String uniqueKey(String prefix) {
        return prefix + "-" + SEQ.getAndIncrement() + ".png";
    }

    // ==================== findDeletableAssets ====================

    @Nested
    @DisplayName("findDeletableAssets")
    class FindDeletableAssets {

        @Test
        @DisplayName("trae solo los assets del status buscado con uploadedAt antes del threshold")
        void returnsOnlyMatchingStatusBeforeThreshold() {
            ZonedDateTime threshold = now();

            ProductCategoryImageAsset deletable = persistAsset(persistCategory(), uniqueKey("category/deletable"),
                    AssetStatus.ORPHANED, threshold.minusDays(1));
            ProductCategoryImageAsset wrongStatus = persistAsset(persistCategory(), uniqueKey("category/wrong-status"),
                    AssetStatus.VALIDATED, threshold.minusDays(1));
            ProductCategoryImageAsset tooRecent = persistAsset(persistCategory(), uniqueKey("category/too-recent"),
                    AssetStatus.ORPHANED, threshold.plusDays(1));

            List<ProductCategoryImageAsset> found = productCategoryImageAssetRepository.findDeletableAssets(
                    AssetStatus.ORPHANED, threshold);

            assertThat(found).extracting(ProductCategoryImageAsset::getId).containsExactly(deletable.getId());
            assertThat(wrongStatus).isNotNull();
            assertThat(tooRecent).isNotNull();
        }

        @Test
        @DisplayName("retorna lista vacía si nada coincide")
        void returnsEmptyWhenNoMatch() {
            persistAsset(persistCategory(), uniqueKey("category/no-match"), AssetStatus.VALIDATED,
                    now().minusDays(1));

            List<ProductCategoryImageAsset> found = productCategoryImageAssetRepository.findDeletableAssets(
                    AssetStatus.ORPHANED, now());

            assertThat(found).isEmpty();
        }
    }
}
