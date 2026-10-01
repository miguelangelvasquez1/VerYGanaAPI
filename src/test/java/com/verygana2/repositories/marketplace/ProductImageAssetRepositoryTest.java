package com.verygana2.repositories.marketplace;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import com.verygana2.models.enums.AssetStatus;
import com.verygana2.models.marketplace.Product;
import com.verygana2.models.marketplace.ProductCategory;
import com.verygana2.models.marketplace.ProductImageAsset;
import com.verygana2.models.userDetails.CommercialDetails;
import com.verygana2.testsupport.TestEntities;

import jakarta.persistence.EntityManager;

/**
 * Tests de integración H2 (modo MySQL) para ProductImageAssetRepository.
 */
@DataJpaTest(properties = {
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:product-image-asset-repo-it;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("ProductImageAssetRepository (integración H2)")
class ProductImageAssetRepositoryTest {

    @Autowired
    private EntityManager em;

    @Autowired
    private ProductImageAssetRepository productImageAssetRepository;

    private static final AtomicLong SEQ = new AtomicLong(1);

    // ==================== HELPERS ====================

    private static ZonedDateTime now() {
        return ZonedDateTime.now();
    }

    private ProductCategory persistCategory() {
        long n = SEQ.getAndIncrement();
        ProductCategory category = new ProductCategory();
        category.setName("categoria-img-" + n);
        em.persist(category);
        em.flush();
        return category;
    }

    private Product persistProduct(CommercialDetails commercial) {
        long n = SEQ.getAndIncrement();
        Product product = new Product();
        product.setCommercial(commercial);
        product.setProductCategory(persistCategory());
        product.setName("Producto imagen " + n);
        product.setPriceCents(60_000L);
        product.setMaxKeysPct(20);
        em.persist(product);
        em.flush();
        return product;
    }

    /**
     * Persiste el asset con objectKey/sizeBytes fijos y status/uploadedAt
     * explícitos (ambos no-nulos, así que el @PrePersist del entity, que solo
     * aplica defaults cuando el campo es null, no los sobrescribe).
     */
    private ProductImageAsset persistAsset(Product product, String objectKey, AssetStatus status,
            ZonedDateTime uploadedAt) {
        ProductImageAsset asset = new ProductImageAsset();
        asset.setObjectKey(objectKey);
        asset.setSizeBytes(2048L);
        asset.setStatus(status);
        asset.setUploadedAt(uploadedAt);
        asset.setProduct(product);
        em.persist(asset);
        em.flush();
        return asset;
    }

    private static String uniqueKey(String prefix) {
        return prefix + "-" + SEQ.getAndIncrement() + ".png";
    }

    // ==================== findByProductId ====================

    @Nested
    @DisplayName("findByProductId")
    class FindByProductId {

        @Test
        @DisplayName("encuentra el asset asociado al producto")
        void findsAssetForProduct() {
            CommercialDetails commercial = TestEntities.persistCommercial(em);
            Product product = persistProduct(commercial);
            ProductImageAsset asset = persistAsset(product, uniqueKey("product/found"), AssetStatus.VALIDATED, now());

            Optional<ProductImageAsset> found = productImageAssetRepository.findByProductId(product.getId());

            assertThat(found).isPresent();
            assertThat(found.get().getId()).isEqualTo(asset.getId());
        }

        @Test
        @DisplayName("retorna vacío si el producto no tiene asset asociado")
        void returnsEmptyWhenNoAsset() {
            CommercialDetails commercial = TestEntities.persistCommercial(em);
            Product product = persistProduct(commercial);

            Optional<ProductImageAsset> found = productImageAssetRepository.findByProductId(product.getId());

            assertThat(found).isEmpty();
        }
    }

    // ==================== findDeletableAssets ====================

    @Nested
    @DisplayName("findDeletableAssets")
    class FindDeletableAssets {

        @Test
        @DisplayName("trae solo los assets del status buscado con uploadedAt antes del threshold")
        void returnsOnlyMatchingStatusBeforeThreshold() {
            CommercialDetails commercial = TestEntities.persistCommercial(em);
            ZonedDateTime threshold = now();

            ProductImageAsset deletable = persistAsset(persistProduct(commercial), uniqueKey("product/deletable"),
                    AssetStatus.ORPHANED, threshold.minusDays(1));
            ProductImageAsset wrongStatus = persistAsset(persistProduct(commercial), uniqueKey("product/wrong-status"),
                    AssetStatus.VALIDATED, threshold.minusDays(1));
            ProductImageAsset tooRecent = persistAsset(persistProduct(commercial), uniqueKey("product/too-recent"),
                    AssetStatus.ORPHANED, threshold.plusDays(1));

            List<ProductImageAsset> found = productImageAssetRepository.findDeletableAssets(AssetStatus.ORPHANED,
                    threshold);

            assertThat(found).extracting(ProductImageAsset::getId).containsExactly(deletable.getId());
            assertThat(wrongStatus).isNotNull();
            assertThat(tooRecent).isNotNull();
        }

        @Test
        @DisplayName("retorna lista vacía si nada coincide")
        void returnsEmptyWhenNoMatch() {
            CommercialDetails commercial = TestEntities.persistCommercial(em);
            persistAsset(persistProduct(commercial), uniqueKey("product/no-match"), AssetStatus.VALIDATED,
                    now().minusDays(1));

            List<ProductImageAsset> found = productImageAssetRepository.findDeletableAssets(AssetStatus.ORPHANED,
                    now());

            assertThat(found).isEmpty();
        }
    }
}
