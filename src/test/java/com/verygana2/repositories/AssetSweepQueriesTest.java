package com.verygana2.repositories;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import com.verygana2.models.ImpactStory.ImpactStory;
import com.verygana2.models.ImpactStory.StoryMediaAsset;
import com.verygana2.models.ImpactStory.StoryMediaAsset.MediaAssetStatus;
import com.verygana2.models.ads.Ad;
import com.verygana2.models.ads.AdAsset;
import com.verygana2.models.branding.Asset;
import com.verygana2.models.enums.AdStatus;
import com.verygana2.models.enums.AssetStatus;
import com.verygana2.models.enums.MediaType;
import com.verygana2.models.finance.PayoutMethod;
import com.verygana2.models.finance.PayoutMethod.VerificationStatus;
import com.verygana2.models.finance.PayoutMethodCertificateAsset;
import com.verygana2.models.marketplace.Product;
import com.verygana2.models.marketplace.ProductCategory;
import com.verygana2.models.marketplace.ProductImageAsset;
import com.verygana2.models.userDetails.CommercialDetails;
import com.verygana2.repositories.finance.PayoutMethodCertificateAssetRepository;
import com.verygana2.repositories.games.AssetRepository;
import com.verygana2.repositories.marketplace.ProductImageAssetRepository;
import com.verygana2.testsupport.TestEntities;

import jakarta.persistence.EntityManager;

/**
 * Consultas que alimentan los barridos de {@code OrphanedAssetsCleanupJob}. Lo que se protege
 * aquí es que un barrido nunca devuelva un archivo que sigue en uso (vinculado a un producto,
 * a un método de pago, a una historia) ni el de un anuncio COMPLETED.
 *
 * <p>Flyway se desactiva: estas tablas salen del DDL de Hibernate, y las migraciones de
 * {@code db/migration} están escritas para MySQL, no para H2.
 */
@DataJpaTest(properties = {
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:asset-sweep-queries-it;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("Consultas de barrido de assets (integración H2)")
class AssetSweepQueriesTest {

    private static final AtomicLong SEQ = new AtomicLong(1);

    @Autowired private EntityManager em;
    @Autowired private ProductImageAssetRepository productImageAssetRepository;
    @Autowired private PayoutMethodCertificateAssetRepository certificateRepository;
    @Autowired private AdAssetRepository adAssetRepository;
    @Autowired private StoryMediaAssetRepository storyMediaAssetRepository;
    @Autowired private AssetRepository assetRepository;

    private static final List<AssetStatus> UPLOAD_IN_PROGRESS = List.of(AssetStatus.PENDING, AssetStatus.VALIDATED);

    // ==================== HELPERS ====================

    private static ZonedDateTime daysAgo(int days) {
        return ZonedDateTime.now().minusDays(days);
    }

    private static String key(String prefix) {
        return prefix + "-" + SEQ.getAndIncrement() + ".png";
    }

    private Product persistProduct(CommercialDetails commercial) {
        long n = SEQ.getAndIncrement();
        ProductCategory category = new ProductCategory();
        category.setName("categoria-sweep-" + n);
        em.persist(category);

        Product product = new Product();
        product.setCommercial(commercial);
        product.setProductCategory(category);
        product.setName("Producto sweep " + n);
        product.setPriceCents(60_000L);
        product.setMaxKeysPct(20);
        em.persist(product);
        em.flush();
        return product;
    }

    private ProductImageAsset persistProductAsset(Product product, AssetStatus status, ZonedDateTime uploadedAt) {
        ProductImageAsset asset = new ProductImageAsset();
        asset.setObjectKey(key("products/sweep"));
        asset.setSizeBytes(1024L);
        asset.setStatus(status);
        asset.setUploadedAt(uploadedAt);
        asset.setProduct(product);
        em.persist(asset);
        em.flush();
        return asset;
    }

    private PayoutMethodCertificateAsset persistCertificate(PayoutMethod method, AssetStatus status,
            ZonedDateTime uploadedAt) {
        PayoutMethodCertificateAsset asset = PayoutMethodCertificateAsset.builder()
                .objectKey(key("payout-certs/sweep"))
                .sizeBytes(1024L)
                .status(status)
                .payoutMethod(method)
                .uploadedAt(uploadedAt)
                .build();
        em.persist(asset);
        em.flush();
        return asset;
    }

    private AdAsset persistAdAssetOfAd(CommercialDetails commercial, AdStatus adStatus, AssetStatus assetStatus) {
        Ad ad = Ad.builder()
                .title("Anuncio sweep " + SEQ.getAndIncrement())
                .description("Fixture de anuncio para el barrido")
                .rewardPerLike(100L)
                .maxLikes(10)
                .currentLikes(0)
                .status(adStatus)
                .createdAt(ZonedDateTime.now())
                .commercial(commercial)
                .build();
        em.persist(ad);

        AdAsset asset = AdAsset.builder()
                .objectKey(key("ads/sweep"))
                .sizeBytes(1024L)
                .mediaType(MediaType.IMAGE)
                .status(assetStatus)
                .uploadedAt(daysAgo(5))
                .ad(ad)
                .build();
        em.persist(asset);
        em.flush();
        return asset;
    }

    // ==================== ProductImageAsset ====================

    @Nested
    @DisplayName("ProductImageAssetRepository.findStaleUnattachedAssets")
    class ProductImageStale {

        @Test
        @DisplayName("trae PENDING/VALIDATED vencidos sin producto; ignora los vinculados, los recientes y otros estados")
        void returnsOnlyStaleUnattached() {
            CommercialDetails commercial = TestEntities.persistCommercial(em);
            Product product = persistProduct(commercial);

            ProductImageAsset stalePending = persistProductAsset(null, AssetStatus.PENDING, daysAgo(3));
            ProductImageAsset staleValidatedNoOwner = persistProductAsset(null, AssetStatus.VALIDATED, daysAgo(3));
            ProductImageAsset inUse = persistProductAsset(product, AssetStatus.VALIDATED, daysAgo(3));
            ProductImageAsset recent = persistProductAsset(null, AssetStatus.PENDING, ZonedDateTime.now());
            ProductImageAsset alreadyDeleted = persistProductAsset(null, AssetStatus.DELETED, daysAgo(3));

            List<ProductImageAsset> found = productImageAssetRepository
                    .findStaleUnattachedAssets(UPLOAD_IN_PROGRESS, ZonedDateTime.now().minusHours(24));

            assertThat(found).extracting(ProductImageAsset::getId)
                    .containsExactlyInAnyOrder(stalePending.getId(), staleValidatedNoOwner.getId())
                    .doesNotContain(inUse.getId(), recent.getId(), alreadyDeleted.getId());
        }
    }

    // ==================== PayoutMethodCertificateAsset ====================

    @Nested
    @DisplayName("PayoutMethodCertificateAssetRepository")
    class Certificates {

        @Test
        @DisplayName("findDeletableAssets: un ORPHANED todavía vinculado a un método de pago NO se devuelve")
        void deletableNeverReturnsAttachedCertificate() {
            CommercialDetails commercial = TestEntities.persistCommercial(em);
            PayoutMethod method = TestEntities.persistPayoutMethod(em, commercial, VerificationStatus.UNDER_REVIEW);

            PayoutMethodCertificateAsset replaced = persistCertificate(null, AssetStatus.ORPHANED, daysAgo(3));
            PayoutMethodCertificateAsset stillAttached = persistCertificate(method, AssetStatus.ORPHANED, daysAgo(3));
            PayoutMethodCertificateAsset recent = persistCertificate(null, AssetStatus.ORPHANED, ZonedDateTime.now());

            List<PayoutMethodCertificateAsset> found = certificateRepository
                    .findDeletableAssets(AssetStatus.ORPHANED, ZonedDateTime.now().minusHours(24));

            assertThat(found).extracting(PayoutMethodCertificateAsset::getId)
                    .containsExactly(replaced.getId())
                    .doesNotContain(stillAttached.getId(), recent.getId());
        }

        @Test
        @DisplayName("findStaleUnattachedAssets: PENDING vencido sin método de pago sí; el certificado en uso no")
        void staleUnattached() {
            CommercialDetails commercial = TestEntities.persistCommercial(em);
            PayoutMethod method = TestEntities.persistPayoutMethod(em, commercial, VerificationStatus.UNDER_REVIEW);

            PayoutMethodCertificateAsset abandoned = persistCertificate(null, AssetStatus.PENDING, daysAgo(3));
            PayoutMethodCertificateAsset inUse = persistCertificate(method, AssetStatus.VALIDATED, daysAgo(3));

            List<PayoutMethodCertificateAsset> found = certificateRepository
                    .findStaleUnattachedAssets(UPLOAD_IN_PROGRESS, ZonedDateTime.now().minusHours(24));

            assertThat(found).extracting(PayoutMethodCertificateAsset::getId)
                    .containsExactly(abandoned.getId())
                    .doesNotContain(inUse.getId());
        }
    }

    // ==================== AdAsset por estado del anuncio ====================

    @Nested
    @DisplayName("AdAssetRepository.findNotDeletedByAdStatus")
    class AdAssetsByAdStatus {

        @Test
        @DisplayName("REJECTED: trae el asset del anuncio rechazado; no el de COMPLETED ni el ya DELETED")
        void rejectedOnly_completedIsNeverSwept() {
            CommercialDetails commercial = TestEntities.persistCommercial(em);

            AdAsset ofRejected = persistAdAssetOfAd(commercial, AdStatus.REJECTED, AssetStatus.VALIDATED);
            AdAsset ofRejectedAlreadyDeleted = persistAdAssetOfAd(commercial, AdStatus.REJECTED, AssetStatus.DELETED);
            AdAsset ofCompleted = persistAdAssetOfAd(commercial, AdStatus.COMPLETED, AssetStatus.VALIDATED);
            AdAsset ofActive = persistAdAssetOfAd(commercial, AdStatus.ACTIVE, AssetStatus.VALIDATED);

            List<AdAsset> rejected = adAssetRepository.findNotDeletedByAdStatus(AdStatus.REJECTED);

            assertThat(rejected).extracting(AdAsset::getId)
                    .containsExactly(ofRejected.getId())
                    .doesNotContain(ofRejectedAlreadyDeleted.getId(), ofCompleted.getId(), ofActive.getId());
        }
    }

    // ==================== StoryMediaAsset ====================

    @Nested
    @DisplayName("StoryMediaAssetRepository.findStaleUnattachedAssets")
    class StoryMedia {

        private StoryMediaAsset persistMedia(ImpactStory story, MediaAssetStatus status) {
            StoryMediaAsset asset = StoryMediaAsset.builder()
                    .objectKey(key("impact-stories/2026/sweep"))
                    .sizeBytes(1024L)
                    .mediaType(MediaType.IMAGE)
                    .status(status)
                    .impactStory(story)
                    .build();
            em.persist(asset);
            em.flush();
            return asset;
        }

        @Test
        @DisplayName("solo la media sin historia; la vinculada a una historia nunca se devuelve")
        void attachedMediaIsNeverReturned() {
            ImpactStory story = ImpactStory.builder()
                    .title("Historia sweep").description("d")
                    .storyDate(LocalDate.now()).beneficiariesCount(1)
                    .investedAmount(BigDecimal.ONE)
                    .build();
            em.persist(story);

            StoryMediaAsset abandoned = persistMedia(null, MediaAssetStatus.PENDING);
            StoryMediaAsset attached = persistMedia(story, MediaAssetStatus.VALIDATED);

            // createdAt lo fija @CreationTimestamp (ahora), así que la edad se controla con el umbral.
            List<StoryMediaAsset> found = storyMediaAssetRepository.findStaleUnattachedAssets(
                    List.of(MediaAssetStatus.PENDING, MediaAssetStatus.VALIDATED), ZonedDateTime.now().plusHours(1));
            assertThat(found).extracting(StoryMediaAsset::getId)
                    .containsExactly(abandoned.getId())
                    .doesNotContain(attached.getId());

            assertThat(storyMediaAssetRepository.findStaleUnattachedAssets(
                    List.of(MediaAssetStatus.PENDING, MediaAssetStatus.VALIDATED), ZonedDateTime.now().minusHours(1)))
                    .as("un asset recién creado no es 'vencido'").isEmpty();
        }
    }

    // ==================== Asset de campaña ====================

    @Nested
    @DisplayName("AssetRepository.findStaleAssets")
    class CampaignAssets {

        private Asset persistCampaignAsset(AssetStatus status) {
            Asset asset = Asset.builder()
                    .objectKey(key("campaigns/designer-1/image/sweep"))
                    .sizeBytes(1024L)
                    .mediaType(MediaType.IMAGE)
                    .status(status)
                    .uploadedBy(1L)
                    .build();
            em.persist(asset);
            em.flush();
            return asset;
        }

        @Test
        @DisplayName("PENDING vencido sí; VALIDATED (en uso) no aunque sea viejo")
        void pendingOnly() {
            Asset pending = persistCampaignAsset(AssetStatus.PENDING);
            Asset validated = persistCampaignAsset(AssetStatus.VALIDATED);

            List<Asset> found = assetRepository.findStaleAssets(
                    List.of(AssetStatus.PENDING), ZonedDateTime.now().plusHours(1));

            assertThat(found).extracting(Asset::getId)
                    .containsExactly(pending.getId())
                    .doesNotContain(validated.getId());
        }
    }
}
