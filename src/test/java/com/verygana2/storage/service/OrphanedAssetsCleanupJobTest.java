package com.verygana2.storage.service;

import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.verygana2.models.ImpactStory.StoryMediaAsset;
import com.verygana2.models.ads.AdAsset;
import com.verygana2.models.branding.Asset;
import com.verygana2.models.branding.CorporateResource;
import com.verygana2.models.commercial.CommercialDocument;
import com.verygana2.models.enums.AdStatus;
import com.verygana2.models.enums.AssetStatus;
import com.verygana2.models.enums.commercial.CommercialDocumentStatus;
import com.verygana2.models.enums.legal.LegalDocumentStatus;
import com.verygana2.models.finance.PayoutMethodCertificateAsset;
import com.verygana2.models.legal.LegalDocument;
import com.verygana2.models.marketplace.ProductImageAsset;
import com.verygana2.models.raffles.PrizeImageAsset;
import com.verygana2.models.raffles.RaffleImageAsset;
import com.verygana2.repositories.AdAssetRepository;
import com.verygana2.repositories.StoryMediaAssetRepository;
import com.verygana2.repositories.branding.CorporateResourceRepository;
import com.verygana2.repositories.commercial.CommercialDocumentRepository;
import com.verygana2.repositories.finance.PayoutMethodCertificateAssetRepository;
import com.verygana2.repositories.games.AssetRepository;
import com.verygana2.repositories.legal.LegalDocumentRepository;
import com.verygana2.repositories.marketplace.ProductCategoryImageAssetRepository;
import com.verygana2.repositories.marketplace.ProductImageAssetRepository;
import com.verygana2.repositories.pqrs.PqrsAssetRepository;
import com.verygana2.repositories.raffles.PrizeImageAssetRepository;
import com.verygana2.repositories.raffles.RaffleImageAssetRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Barridos de {@link OrphanedAssetsCleanupJob}. Lo central es que cada uno borre de R2 con el
 * prefijo con el que se subió el archivo ({@code public/} o {@code private/}): un prefijo
 * equivocado no falla (borrar una key inexistente es válido en S3/R2), deja la fila en DELETED y
 * el archivo en el bucket. Los mocks de R2 verifican la key exacta para que ese error no pase.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OrphanedAssetsCleanupJob")
class OrphanedAssetsCleanupJobTest {

    @Mock private AdAssetRepository adAssetRepository;
    @Mock private AssetRepository assetRepository;
    @Mock private StoryMediaAssetRepository storyMediaAssetRepository;
    @Mock private ProductImageAssetRepository productImageAssetRepository;
    @Mock private ProductCategoryImageAssetRepository productCategoryImageAssetRepository;
    @Mock private PqrsAssetRepository pqrsAssetRepository;
    @Mock private RaffleImageAssetRepository raffleImageAssetRepository;
    @Mock private PrizeImageAssetRepository prizeImageAssetRepository;
    @Mock private PayoutMethodCertificateAssetRepository payoutMethodCertificateAssetRepository;
    @Mock private CorporateResourceRepository corporateResourceRepository;
    @Mock private CommercialDocumentRepository commercialDocumentRepository;
    @Mock private LegalDocumentRepository legalDocumentRepository;
    @Mock private R2Service r2Service;

    private OrphanedAssetsCleanupJob job;

    @BeforeEach
    void setUp() {
        job = new OrphanedAssetsCleanupJob(adAssetRepository, assetRepository,
                storyMediaAssetRepository, productImageAssetRepository, productCategoryImageAssetRepository,
                pqrsAssetRepository, raffleImageAssetRepository, prizeImageAssetRepository,
                payoutMethodCertificateAssetRepository, corporateResourceRepository,
                commercialDocumentRepository, legalDocumentRepository, r2Service);
        ReflectionTestUtils.setField(job, "maxAgeHours", 24);
    }

    private static ZonedDateTime old() {
        return ZonedDateTime.now().minusDays(2);
    }

    // ─── Rifas / premios ────────────────────────────────────────────────────

    @Nested
    @DisplayName("rifas y premios")
    class Raffles {

        @Test
        @DisplayName("cleanupRaffleImageAssets: borra de R2 y marca DELETED los assets ORPHANED vencidos")
        void cleanupRaffleImageAssets_deletesOrphanedAssets() {
            RaffleImageAsset asset = RaffleImageAsset.builder()
                    .id(1L)
                    .objectKey("raffles/foo.jpg")
                    .status(AssetStatus.ORPHANED)
                    .uploadedAt(old())
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
            RaffleImageAsset asset = RaffleImageAsset.builder()
                    .id(1L)
                    .objectKey("raffles/foo.jpg")
                    .status(AssetStatus.ORPHANED)
                    .uploadedAt(old())
                    .build();
            when(raffleImageAssetRepository.findDeletableAssets(eq(AssetStatus.ORPHANED), any()))
                    .thenReturn(List.of(asset));
            doThrow(new RuntimeException("R2 down")).when(r2Service).deleteObject(any());

            job.cleanupRaffleImageAssets();

            verify(raffleImageAssetRepository, never()).save(any());
            assertThat(asset.getStatus()).isEqualTo(AssetStatus.ORPHANED);
        }

        @Test
        @DisplayName("cleanupRaffleImageAssets: una subida sin vincular y vencida (nunca confirmada) se orfana y se borra")
        void cleanupRaffleImageAssets_abandonedUpload_isOrphanedThenDeleted() {
            RaffleImageAsset abandoned = RaffleImageAsset.builder()
                    .id(3L)
                    .objectKey("raffles/abandoned.jpg")
                    .status(AssetStatus.PENDING)
                    .uploadedAt(old())
                    .build();
            when(raffleImageAssetRepository.findStaleUnattachedAssets(any(), any()))
                    .thenReturn(List.of(abandoned));
            // Tras el flush del paso 1 la BD ya lo devuelve como ORPHANED.
            when(raffleImageAssetRepository.findDeletableAssets(eq(AssetStatus.ORPHANED), any()))
                    .thenReturn(List.of(abandoned));

            job.cleanupRaffleImageAssets();

            verify(raffleImageAssetRepository).saveAll(List.of(abandoned));
            verify(r2Service).deleteObject("public/raffles/abandoned.jpg");
            assertThat(abandoned.getStatus()).isEqualTo(AssetStatus.DELETED);
        }

        @Test
        @DisplayName("cleanupPrizeImageAssets: borra de R2 y marca DELETED los assets ORPHANED vencidos")
        void cleanupPrizeImageAssets_deletesOrphanedAssets() {
            PrizeImageAsset asset = PrizeImageAsset.builder()
                    .id(2L)
                    .objectKey("prizes/raffle-1/foo.jpg")
                    .status(AssetStatus.ORPHANED)
                    .uploadedAt(old())
                    .build();
            when(prizeImageAssetRepository.findDeletableAssets(eq(AssetStatus.ORPHANED), any()))
                    .thenReturn(List.of(asset));

            job.cleanupPrizeImageAssets();

            verify(r2Service).deleteObject("public/prizes/raffle-1/foo.jpg");
            verify(prizeImageAssetRepository).save(asset);
            assertThat(asset.getStatus()).isEqualTo(AssetStatus.DELETED);
        }
    }

    // ─── Assets que se suben a public/ y el job borraba sin prefijo ─────────

    @Nested
    @DisplayName("campañas e historias de impacto (se suben a public/)")
    class PublicUploads {

        @Test
        @DisplayName("cleanupAssets: el asset de campaña se borra en public/<key>, no en <key> (que no existe)")
        void cleanupAssets_deletesWithPublicPrefix() {
            Asset asset = Asset.builder()
                    .id(10L)
                    .objectKey("campaigns/designer-5/image/abc-123")
                    .status(AssetStatus.ORPHANED)
                    .build();
            when(assetRepository.findDeletableAssets(eq(AssetStatus.ORPHANED), any())).thenReturn(List.of(asset));

            job.cleanupAssets();

            verify(r2Service).deleteObject("public/campaigns/designer-5/image/abc-123");
            verify(r2Service, never()).deleteObject("campaigns/designer-5/image/abc-123");
            assertThat(asset.getStatus()).isEqualTo(AssetStatus.DELETED);
        }

        @Test
        @DisplayName("cleanupAssets: un PENDING de campaña vencido (el diseñador nunca confirmó) se orfana y se borra")
        void cleanupAssets_abandonedPending_isOrphanedThenDeleted() {
            Asset pending = Asset.builder()
                    .id(11L)
                    .objectKey("campaigns/designer-5/image/pending-1")
                    .status(AssetStatus.PENDING)
                    .build();
            when(assetRepository.findStaleAssets(eq(List.of(AssetStatus.PENDING)), any())).thenReturn(List.of(pending));
            when(assetRepository.findDeletableAssets(eq(AssetStatus.ORPHANED), any())).thenReturn(List.of(pending));

            job.cleanupAssets();

            verify(assetRepository).saveAll(List.of(pending));
            verify(r2Service).deleteObject("public/campaigns/designer-5/image/pending-1");
            assertThat(pending.getStatus()).isEqualTo(AssetStatus.DELETED);
        }

        @Test
        @DisplayName("cleanupAssets: no toca los VALIDATED (asset de campaña en uso)")
        void cleanupAssets_neverSweepsValidated() {
            job.cleanupAssets();

            // Solo se pide PENDING al paso de "subidas abandonadas".
            verify(assetRepository).findStaleAssets(eq(List.of(AssetStatus.PENDING)), any());
            verify(r2Service, never()).deleteObject(any());
        }

        @Test
        @DisplayName("cleanupImpactStoriesAssets: la media de historias se borra en public/<key>, no en <key>")
        void cleanupImpactStoriesAssets_deletesWithPublicPrefix() {
            StoryMediaAsset asset = StoryMediaAsset.builder()
                    .id(20L)
                    .objectKey("impact-stories/2026/abc.jpg")
                    .status(StoryMediaAsset.MediaAssetStatus.ORPHANED)
                    .build();
            when(storyMediaAssetRepository.findDeletableAssets(eq(StoryMediaAsset.MediaAssetStatus.ORPHANED), any()))
                    .thenReturn(List.of(asset));

            job.cleanupImpactStoriesAssets();

            verify(r2Service).deleteObject("public/impact-stories/2026/abc.jpg");
            verify(r2Service, never()).deleteObject("impact-stories/2026/abc.jpg");
            assertThat(asset.getStatus()).isEqualTo(StoryMediaAsset.MediaAssetStatus.DELETED);
        }

        @Test
        @DisplayName("cleanupImpactStoriesAssets: media sin historia y vencida (creación abandonada) se orfana y se borra")
        void cleanupImpactStoriesAssets_abandonedUpload_isOrphanedThenDeleted() {
            StoryMediaAsset pending = StoryMediaAsset.builder()
                    .id(21L)
                    .objectKey("impact-stories/2026/pending.jpg")
                    .status(StoryMediaAsset.MediaAssetStatus.PENDING)
                    .build();
            when(storyMediaAssetRepository.findStaleUnattachedAssets(any(), any())).thenReturn(List.of(pending));
            when(storyMediaAssetRepository.findDeletableAssets(eq(StoryMediaAsset.MediaAssetStatus.ORPHANED), any()))
                    .thenReturn(List.of(pending));

            job.cleanupImpactStoriesAssets();

            verify(storyMediaAssetRepository).saveAll(List.of(pending));
            verify(r2Service).deleteObject("public/impact-stories/2026/pending.jpg");
            assertThat(pending.getStatus()).isEqualTo(StoryMediaAsset.MediaAssetStatus.DELETED);
        }
    }

    // ─── Productos ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("imágenes de producto")
    class ProductImages {

        @Test
        @DisplayName("una subida sin vincular y vencida se orfana; luego se borra en private/ y public/")
        void abandonedUpload_isOrphanedThenDeletedInBothPrefixes() {
            ProductImageAsset abandoned = new ProductImageAsset();
            abandoned.setId(30L);
            abandoned.setObjectKey("products/1/img.jpg");
            abandoned.setStatus(AssetStatus.VALIDATED); // p. ej. el asset reemplazado que quedó sin dueño
            when(productImageAssetRepository.findStaleUnattachedAssets(any(), any())).thenReturn(List.of(abandoned));
            when(productImageAssetRepository.findDeletableAssets(eq(AssetStatus.ORPHANED), any()))
                    .thenReturn(List.of(abandoned));

            job.cleanupProductImageAssets();

            verify(productImageAssetRepository).saveAll(List.of(abandoned));
            verify(r2Service).deleteObject("private/products/1/img.jpg");
            verify(r2Service).deleteObject("public/products/1/img.jpg");
            assertThat(abandoned.getStatus()).isEqualTo(AssetStatus.DELETED);
        }
    }

    // ─── Anuncios ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("anuncios")
    class Ads {

        @Test
        @DisplayName("cleanupRejectedAdAssets: borra en private/<key> el archivo del anuncio REJECTED y marca el asset DELETED")
        void rejectedAdAsset_isDeleted() {
            AdAsset asset = AdAsset.builder().id(40L).objectKey("ads/commercial-1/1-abc.mp4")
                    .status(AssetStatus.VALIDATED).build();
            when(adAssetRepository.findNotDeletedByAdStatus(AdStatus.REJECTED)).thenReturn(List.of(asset));

            job.cleanupRejectedAdAssets();

            verify(r2Service).deleteObject("private/ads/commercial-1/1-abc.mp4");
            verify(adAssetRepository).save(asset);
            assertThat(asset.getStatus()).isEqualTo(AssetStatus.DELETED);
        }

        @Test
        @DisplayName("cleanupRejectedAdAssets: si R2 falla el asset no pasa a DELETED (se reintenta en la próxima corrida)")
        void rejectedAdAsset_r2Failure_isRetriedLater() {
            AdAsset asset = AdAsset.builder().id(41L).objectKey("ads/commercial-1/2-abc.mp4")
                    .status(AssetStatus.VALIDATED).build();
            when(adAssetRepository.findNotDeletedByAdStatus(AdStatus.REJECTED)).thenReturn(List.of(asset));
            doThrow(new RuntimeException("R2 down")).when(r2Service).deleteObject(any());

            job.cleanupRejectedAdAssets();

            verify(adAssetRepository, never()).save(any());
            assertThat(asset.getStatus()).isEqualTo(AssetStatus.VALIDATED);
        }

        @Test
        @DisplayName("los anuncios COMPLETED nunca se barren: pueden reabrirse aumentando el presupuesto y necesitan su archivo")
        void completedAdAssets_areNeverSwept() {
            job.cleanupRejectedAdAssets();
            job.cleanupAdAssets();

            verify(adAssetRepository).findNotDeletedByAdStatus(AdStatus.REJECTED);
            verify(adAssetRepository, never()).findNotDeletedByAdStatus(AdStatus.COMPLETED);
            verify(r2Service, never()).deleteObject(any());
        }
    }

    // ─── Certificaciones de payout ──────────────────────────────────────────

    @Nested
    @DisplayName("certificaciones bancarias de payout (private/)")
    class PayoutCertificates {

        @Test
        @DisplayName("la certificación reemplazada (ORPHANED, desvinculada) se borra en private/<key>")
        void orphanedCertificate_isDeleted() {
            PayoutMethodCertificateAsset asset = PayoutMethodCertificateAsset.builder()
                    .id(50L).objectKey("payout-certs/7/cert.pdf").status(AssetStatus.ORPHANED).build();
            when(payoutMethodCertificateAssetRepository.findDeletableAssets(eq(AssetStatus.ORPHANED), any()))
                    .thenReturn(List.of(asset));

            job.cleanupPayoutMethodCertificateAssets();

            verify(r2Service).deleteObject("private/payout-certs/7/cert.pdf");
            verify(payoutMethodCertificateAssetRepository).save(asset);
            assertThat(asset.getStatus()).isEqualTo(AssetStatus.DELETED);
        }

        @Test
        @DisplayName("una subida sin vincular y vencida se orfana y se borra")
        void abandonedUpload_isOrphanedThenDeleted() {
            PayoutMethodCertificateAsset pending = PayoutMethodCertificateAsset.builder()
                    .id(51L).objectKey("payout-certs/7/pending.pdf").status(AssetStatus.PENDING).build();
            when(payoutMethodCertificateAssetRepository.findStaleUnattachedAssets(any(), any()))
                    .thenReturn(List.of(pending));
            when(payoutMethodCertificateAssetRepository.findDeletableAssets(eq(AssetStatus.ORPHANED), any()))
                    .thenReturn(List.of(pending));

            job.cleanupPayoutMethodCertificateAssets();

            verify(payoutMethodCertificateAssetRepository).saveAll(List.of(pending));
            verify(r2Service).deleteObject("private/payout-certs/7/pending.pdf");
            assertThat(pending.getStatus()).isEqualTo(AssetStatus.DELETED);
        }
    }

    // ─── Recursos corporativos ──────────────────────────────────────────────

    @Nested
    @DisplayName("recursos corporativos de branding (private/)")
    class CorporateResources {

        @Test
        @DisplayName("un recurso ORPHANED se borra en private/<key>")
        void orphanedResource_isDeleted() {
            CorporateResource resource = CorporateResource.builder()
                    .id(60L).objectKey("branding/9/resources/abc.png").status(AssetStatus.ORPHANED).build();
            when(corporateResourceRepository.findDeletableAssets(eq(AssetStatus.PENDING), any()))
                    .thenReturn(List.of());
            when(corporateResourceRepository.findDeletableAssets(eq(AssetStatus.ORPHANED), any()))
                    .thenReturn(List.of(resource));

            job.cleanupCorporateResources();

            verify(r2Service).deleteObject("private/branding/9/resources/abc.png");
            verify(corporateResourceRepository).save(resource);
            assertThat(resource.getStatus()).isEqualTo(AssetStatus.DELETED);
        }

        @Test
        @DisplayName("un PENDING vencido (subida nunca confirmada) se orfana y se borra; un VALIDATED no se pide nunca")
        void abandonedPending_isOrphanedThenDeleted() {
            CorporateResource pending = CorporateResource.builder()
                    .id(61L).objectKey("branding/9/resources/pending.png").status(AssetStatus.PENDING).build();
            when(corporateResourceRepository.findDeletableAssets(eq(AssetStatus.PENDING), any()))
                    .thenReturn(List.of(pending));
            when(corporateResourceRepository.findDeletableAssets(eq(AssetStatus.ORPHANED), any()))
                    .thenReturn(List.of(pending));

            job.cleanupCorporateResources();

            verify(corporateResourceRepository).saveAll(List.of(pending));
            verify(r2Service).deleteObject("private/branding/9/resources/pending.png");
            assertThat(pending.getStatus()).isEqualTo(AssetStatus.DELETED);
            verify(corporateResourceRepository, never()).findDeletableAssets(eq(AssetStatus.VALIDATED), any());
        }
    }

    // ─── Documentos ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("documentos nunca confirmados")
    class Documents {

        @Test
        @DisplayName("documento comercial PENDING vencido: se borra en private/<key> y pasa a ORPHANED")
        void commercialDocument_pendingIsDeleted() {
            CommercialDocument doc = new CommercialDocument();
            doc.setId(70L);
            doc.setObjectKey("commercial-documents/3/uuid");
            doc.setStatus(CommercialDocumentStatus.PENDING);
            when(commercialDocumentRepository.findByStatusAndUploadedAtBefore(eq(CommercialDocumentStatus.PENDING), any()))
                    .thenReturn(List.of(doc));

            job.cleanupCommercialDocuments();

            verify(r2Service).deleteObject("private/commercial-documents/3/uuid");
            verify(commercialDocumentRepository).save(doc);
            assertThat(doc.getStatus()).isEqualTo(CommercialDocumentStatus.ORPHANED);
        }

        @Test
        @DisplayName("documento comercial: si R2 falla queda PENDING para reintentar")
        void commercialDocument_r2FailureStaysPending() {
            CommercialDocument doc = new CommercialDocument();
            doc.setId(71L);
            doc.setObjectKey("commercial-documents/3/uuid2");
            doc.setStatus(CommercialDocumentStatus.PENDING);
            when(commercialDocumentRepository.findByStatusAndUploadedAtBefore(eq(CommercialDocumentStatus.PENDING), any()))
                    .thenReturn(List.of(doc));
            doThrow(new RuntimeException("R2 down")).when(r2Service).deleteObject(any());

            job.cleanupCommercialDocuments();

            verify(commercialDocumentRepository, never()).save(any());
            assertThat(doc.getStatus()).isEqualTo(CommercialDocumentStatus.PENDING);
        }

        @Test
        @DisplayName("documento legal PENDING vencido: se borra en public/<key> y se elimina la fila")
        void legalDocument_pendingIsDeleted() {
            LegalDocument doc = new LegalDocument();
            doc.setId(80L);
            doc.setObjectKey("legal/terms/3-uuid.pdf");
            doc.setStatus(LegalDocumentStatus.PENDING);
            when(legalDocumentRepository.findByStatusAndCreatedAtBefore(eq(LegalDocumentStatus.PENDING), any()))
                    .thenReturn(List.of(doc));

            job.cleanupLegalDocuments();

            verify(r2Service).deleteObject("public/legal/terms/3-uuid.pdf");
            verify(legalDocumentRepository).delete(doc);
        }

        @Test
        @DisplayName("documento legal: si R2 falla no se elimina la fila (así el archivo no queda sin referencia)")
        void legalDocument_r2FailureKeepsRow() {
            LegalDocument doc = new LegalDocument();
            doc.setId(81L);
            doc.setObjectKey("legal/terms/4-uuid.pdf");
            doc.setStatus(LegalDocumentStatus.PENDING);
            when(legalDocumentRepository.findByStatusAndCreatedAtBefore(eq(LegalDocumentStatus.PENDING), any()))
                    .thenReturn(List.of(doc));
            doThrow(new RuntimeException("R2 down")).when(r2Service).deleteObject(any());

            job.cleanupLegalDocuments();

            verify(legalDocumentRepository, never()).delete(any());
        }
    }
}
