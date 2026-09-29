package com.verygana2.storage.service;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.verygana2.models.ImpactStory.StoryMediaAsset;
import com.verygana2.models.ads.AdAsset;
import com.verygana2.models.branding.Asset;
import com.verygana2.models.branding.CorporateResource;
import com.verygana2.models.commercial.CommercialDocument;
import com.verygana2.models.enums.AdStatus;
import com.verygana2.models.enums.AssetStatus;
import com.verygana2.models.enums.commercial.CommercialDocumentStatus;
import com.verygana2.models.enums.legal.LegalDocumentStatus;
import com.verygana2.models.enums.pqrs.PqrsAssetStatus;
import com.verygana2.models.finance.PayoutMethodCertificateAsset;
import com.verygana2.models.legal.LegalDocument;
import com.verygana2.models.marketplace.ProductCategoryImageAsset;
import com.verygana2.models.marketplace.ProductImageAsset;
import com.verygana2.models.pqrs.PqrsAsset;
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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Scheduled job to clean up orphaned assets in R2.
 * Runs automatically to free up storage space.
 *
 * <p><b>Prefijos de R2.</b> {@code R2Service#generateUploadUrl(isPrivate, key, ...)} guarda el
 * objeto en {@code private/<key>} o {@code public/<key>}, pero las filas guardan la key sin
 * prefijo. Cada barrido debe borrar con el prefijo con el que se subió el archivo: borrar una
 * key inexistente no falla en S3/R2, así que un prefijo equivocado deja la fila en DELETED con
 * el archivo todavía en el bucket.
 *
 * <p><b>Dos pasos por tipo.</b> (1) Los assets que se subieron y nunca se vincularon (PENDING, o
 * VALIDATED sin dueño) y ya vencieron pasan a ORPHANED: cubre al usuario que sube el archivo y
 * abandona el flujo sin llamar al confirm. Solo se toman los que siguen sin vincular, así que un
 * archivo en uso nunca entra. (2) Todo lo ORPHANED vencido se borra de R2 y pasa a DELETED; si el
 * borrado falla el asset sigue ORPHANED y se reintenta en la siguiente corrida.
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

    /** Estados de un asset "en subida": pedido el upload pero sin dueño que lo use. */
    private static final List<AssetStatus> UPLOAD_IN_PROGRESS = List.of(AssetStatus.PENDING, AssetStatus.VALIDATED);

    private final AdAssetRepository adAssetRepository;
    private final AssetRepository assetRepository;
    private final StoryMediaAssetRepository storyMediaAssetRepository;
    private final ProductImageAssetRepository productImageAssetRepository;
    private final ProductCategoryImageAssetRepository productCategoryImageAssetRepository;
    private final PqrsAssetRepository pqrsAssetRepository;
    private final RaffleImageAssetRepository raffleImageAssetRepository;
    private final PrizeImageAssetRepository prizeImageAssetRepository;
    private final PayoutMethodCertificateAssetRepository payoutMethodCertificateAssetRepository;
    private final CorporateResourceRepository corporateResourceRepository;
    private final CommercialDocumentRepository commercialDocumentRepository;
    private final LegalDocumentRepository legalDocumentRepository;
    private final R2Service r2Service;

    @Value("${cleanup.orphaned-assets.max-age-hours:24}")
    private int maxAgeHours; // Configurable: how long to wait before deleting orphaned assets

    // For Ads
    @Transactional
    @Scheduled(cron = "0 0 * * * *") // every hour
    public void cleanupAdAssets() {

        ZonedDateTime threshold = ZonedDateTime.now().minusHours(maxAgeHours);

        // 1. Orfanar assets que se subieron/analizaron pero nunca se vincularon a un
        //    anuncio (flujo abandonado, o análisis colgado en ANALYZING). Los que ya
        //    tienen ad quedan intactos. Así una cotización de precio vieja deja de ser
        //    utilizable y el asset entra al ciclo normal de borrado.
        List<AdAsset> stale = adAssetRepository.findStaleUnattachedAssets(
                List.of(AssetStatus.PENDING, AssetStatus.ANALYZING, AssetStatus.VALIDATED), threshold);
        for (AdAsset asset : stale) {
            asset.setStatus(AssetStatus.ORPHANED);
            log.info("Ad Asset {} orphaned: sin anuncio y más viejo que {}h", asset.getId(), maxAgeHours);
        }
        adAssetRepository.saveAll(stale);

        // 2. Borrar de R2 todo lo que esté ORPHANED y con edad suficiente (incluye lo
        //    recién orfanado en el paso 1, que ya cumple uploadedAt < threshold).
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

    // For Ads REJECTED
    @Transactional
    @Scheduled(cron = "0 0 * * * *") // every hour
    public void cleanupRejectedAdAssets() {

        // REJECTED es terminal (reembolsado, no editable, no reactivable) y un anuncio
        // rechazado nunca llegó a aprobarse, así que su archivo sigue solo en "private/".
        // COMPLETED NO se barre a propósito: un anuncio completado puede reabrirse
        // aumentando su presupuesto y necesita su archivo.
        List<AdAsset> assets = adAssetRepository.findNotDeletedByAdStatus(AdStatus.REJECTED);
        log.info("Cleanup job: {} candidate rejected ad assets", assets.size());

        for (AdAsset asset : assets) {
            try {
                r2Service.deleteObject("private/" + asset.getObjectKey());
                asset.setStatus(AssetStatus.DELETED);
                adAssetRepository.save(asset);

                log.info("Ad Asset {} of rejected ad deleted from R2", asset.getId());

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

        orphanStale("Impact Story Asset",
                storyMediaAssetRepository.findStaleUnattachedAssets(
                        List.of(StoryMediaAsset.MediaAssetStatus.PENDING, StoryMediaAsset.MediaAssetStatus.VALIDATED),
                        threshold),
                StoryMediaAsset::getId,
                a -> a.setStatus(StoryMediaAsset.MediaAssetStatus.ORPHANED),
                storyMediaAssetRepository::saveAll);

        List<StoryMediaAsset> assets = storyMediaAssetRepository.findDeletableAssets( StoryMediaAsset.MediaAssetStatus.ORPHANED, threshold);
        log.info("Cleanup job: {} candidate impact story assets", assets.size());

        for (StoryMediaAsset asset : assets) {
            try {
                // Se sube con isPrivate=false (StoryMediaAssetServiceImpl#prepareUpload).
                r2Service.deleteObject("public/" + asset.getObjectKey());
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

        // Solo PENDING: en assets de campaña VALIDATED es el estado normal de uso (no hay un
        // padre contra el cual comprobar "sin vincular"), y el front confirma justo después de subir.
        orphanStale("Campaign Asset",
                assetRepository.findStaleAssets(List.of(AssetStatus.PENDING), threshold),
                Asset::getId,
                Asset::markAsOrphan,
                assetRepository::saveAll);

        List<Asset> assets = assetRepository.findDeletableAssets(AssetStatus.ORPHANED, threshold);

        log.info("Cleanup job: {} candidate campaign assets", assets.size());

        for (Asset asset : assets) {
            try {
                // Se sube con isPrivate=false (GameDesignerServiceImpl#generateUploadUrl).
                r2Service.deleteObject("public/" + asset.getObjectKey());
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

    // For product images
    @Transactional
    @Scheduled(cron = "0 0 * * * *") // every hour
    public void cleanupProductImageAssets() {

        ZonedDateTime threshold = ZonedDateTime.now().minusHours(maxAgeHours);

        orphanStale("Product Image Asset",
                productImageAssetRepository.findStaleUnattachedAssets(UPLOAD_IN_PROGRESS, threshold),
                ProductImageAsset::getId,
                a -> a.setStatus(AssetStatus.ORPHANED),
                productImageAssetRepository::saveAll);

        List<ProductImageAsset> assets = productImageAssetRepository.findDeletableAssets(AssetStatus.ORPHANED, threshold);
        log.info("Cleanup job: {} candidate product image assets", assets.size());

        for (ProductImageAsset asset : assets) {
            try {
                // El asset ya perdió la referencia al producto al orfanarse, así que
                // no sabemos si alcanzó a copiarse a "public/" (aprobación) o seguía
                // en "private/" (nunca aprobado). Borrar ambos es seguro: un delete
                // de una key inexistente en S3/R2 no falla.
                r2Service.deleteObject("private/" + asset.getObjectKey());
                r2Service.deleteObject("public/" + asset.getObjectKey());
                asset.setStatus(AssetStatus.DELETED);
                productImageAssetRepository.save(asset);

                log.info("Product Image Asset {} deleted from R2", asset.getId());

            } catch (Exception e) {
                log.warn("Failed to delete asset {} ({}): {}", asset.getId(), asset.getObjectKey(), e.getMessage());
            }
        }
    }

    // For product category images
    @Transactional
    @Scheduled(cron = "0 0 * * * *") // every hour
    public void cleanupProductCategoryImageAssets() {

        ZonedDateTime threshold = ZonedDateTime.now().minusHours(maxAgeHours);

        orphanStale("Product Category Image Asset",
                productCategoryImageAssetRepository.findStaleUnattachedAssets(UPLOAD_IN_PROGRESS, threshold),
                ProductCategoryImageAsset::getId,
                a -> a.setStatus(AssetStatus.ORPHANED),
                productCategoryImageAssetRepository::saveAll);

        List<ProductCategoryImageAsset> assets = productCategoryImageAssetRepository
                .findDeletableAssets(AssetStatus.ORPHANED, threshold);
        log.info("Cleanup job: {} candidate product category image assets", assets.size());

        for (ProductCategoryImageAsset asset : assets) {
            try {
                r2Service.deleteObject("public/" + asset.getObjectKey());
                asset.setStatus(AssetStatus.DELETED);
                productCategoryImageAssetRepository.save(asset);

                log.info("Product Category Image Asset {} deleted from R2", asset.getId());

            } catch (Exception e) {
                log.warn("Failed to delete asset {} ({}): {}", asset.getId(), asset.getObjectKey(), e.getMessage());
            }
        }
    }

    // For PQRS evidence
    @Transactional
    @Scheduled(cron = "0 0 * * * *") // every hour
    public void cleanupPqrsAssets() {

        ZonedDateTime threshold = ZonedDateTime.now().minusHours(maxAgeHours);

        // Barre PENDING/ORPHANED vencidos y también VALIDATED-sin-reclamar
        // vencidos (usuario subió evidencia pero nunca radicó el PQRS) — ver
        // PqrsAssetRepository.findDeletableAssets.
        List<PqrsAsset> assets = pqrsAssetRepository.findDeletableAssets(threshold);
        log.info("Cleanup job: {} candidate pqrs assets", assets.size());

        for (PqrsAsset asset : assets) {
            try {
                r2Service.deleteObject("private/" + asset.getObjectKey());
                asset.setStatus(PqrsAssetStatus.DELETED);
                pqrsAssetRepository.save(asset);

                log.info("PQRS Asset {} deleted from R2", asset.getId());

            } catch (Exception e) {
                log.warn("Failed to delete asset {} ({}): {}", asset.getId(), asset.getObjectKey(), e.getMessage());
            }
        }
    }

    // For raffle cover images
    @Transactional
    @Scheduled(cron = "0 0 * * * *") // every hour
    public void cleanupRaffleImageAssets() {

        ZonedDateTime threshold = ZonedDateTime.now().minusHours(maxAgeHours);

        orphanStale("Raffle Image Asset",
                raffleImageAssetRepository.findStaleUnattachedAssets(UPLOAD_IN_PROGRESS, threshold),
                RaffleImageAsset::getId,
                a -> a.setStatus(AssetStatus.ORPHANED),
                raffleImageAssetRepository::saveAll);

        List<RaffleImageAsset> assets = raffleImageAssetRepository.findDeletableAssets(AssetStatus.ORPHANED, threshold);
        log.info("Cleanup job: {} candidate raffle image assets", assets.size());

        for (RaffleImageAsset asset : assets) {
            try {
                r2Service.deleteObject("public/" + asset.getObjectKey());
                asset.setStatus(AssetStatus.DELETED);
                raffleImageAssetRepository.save(asset);

                log.info("Raffle Image Asset {} deleted from R2", asset.getId());

            } catch (Exception e) {
                log.warn("Failed to delete asset {} ({}): {}", asset.getId(), asset.getObjectKey(), e.getMessage());
            }
        }
    }

    // For raffle prize images
    @Transactional
    @Scheduled(cron = "0 0 * * * *") // every hour
    public void cleanupPrizeImageAssets() {

        ZonedDateTime threshold = ZonedDateTime.now().minusHours(maxAgeHours);

        orphanStale("Prize Image Asset",
                prizeImageAssetRepository.findStaleUnattachedAssets(UPLOAD_IN_PROGRESS, threshold),
                PrizeImageAsset::getId,
                a -> a.setStatus(AssetStatus.ORPHANED),
                prizeImageAssetRepository::saveAll);

        List<PrizeImageAsset> assets = prizeImageAssetRepository.findDeletableAssets(AssetStatus.ORPHANED, threshold);
        log.info("Cleanup job: {} candidate prize image assets", assets.size());

        for (PrizeImageAsset asset : assets) {
            try {
                r2Service.deleteObject("public/" + asset.getObjectKey());
                asset.setStatus(AssetStatus.DELETED);
                prizeImageAssetRepository.save(asset);

                log.info("Prize Image Asset {} deleted from R2", asset.getId());

            } catch (Exception e) {
                log.warn("Failed to delete asset {} ({}): {}", asset.getId(), asset.getObjectKey(), e.getMessage());
            }
        }
    }

    // For bank certifications of payout methods
    @Transactional
    @Scheduled(cron = "0 0 * * * *") // every hour
    public void cleanupPayoutMethodCertificateAssets() {

        ZonedDateTime threshold = ZonedDateTime.now().minusHours(maxAgeHours);

        orphanStale("Payout Method Certificate Asset",
                payoutMethodCertificateAssetRepository.findStaleUnattachedAssets(UPLOAD_IN_PROGRESS, threshold),
                PayoutMethodCertificateAsset::getId,
                a -> a.setStatus(AssetStatus.ORPHANED),
                payoutMethodCertificateAssetRepository::saveAll);

        // findDeletableAssets solo devuelve las ya desvinculadas de su método de pago.
        List<PayoutMethodCertificateAsset> assets = payoutMethodCertificateAssetRepository
                .findDeletableAssets(AssetStatus.ORPHANED, threshold);
        log.info("Cleanup job: {} candidate payout method certificate assets", assets.size());

        for (PayoutMethodCertificateAsset asset : assets) {
            try {
                // Se sube con isPrivate=true (PayoutMethodServiceImpl#prepareCertificateUpload).
                r2Service.deleteObject("private/" + asset.getObjectKey());
                asset.setStatus(AssetStatus.DELETED);
                payoutMethodCertificateAssetRepository.save(asset);

                log.info("Payout Method Certificate Asset {} deleted from R2", asset.getId());

            } catch (Exception e) {
                log.warn("Failed to delete asset {} ({}): {}", asset.getId(), asset.getObjectKey(), e.getMessage());
            }
        }
    }

    // For corporate resources of branding requests
    @Transactional
    @Scheduled(cron = "0 0 * * * *") // every hour
    public void cleanupCorporateResources() {

        ZonedDateTime threshold = ZonedDateTime.now().minusHours(maxAgeHours);

        // Un recurso nace vinculado a su solicitud: aquí el estado es lo que separa un archivo
        // en uso (VALIDATED) de una subida nunca confirmada (PENDING).
        orphanStale("Corporate Resource",
                corporateResourceRepository.findDeletableAssets(AssetStatus.PENDING, threshold),
                CorporateResource::getId,
                CorporateResource::markAsOrphan,
                corporateResourceRepository::saveAll);

        List<CorporateResource> resources = corporateResourceRepository.findDeletableAssets(AssetStatus.ORPHANED, threshold);
        log.info("Cleanup job: {} candidate corporate resources", resources.size());

        for (CorporateResource resource : resources) {
            try {
                // Se sube con isPrivate=true (BrandingRequestServiceImpl#generateResourceUploadUrl).
                r2Service.deleteObject("private/" + resource.getObjectKey());
                resource.setStatus(AssetStatus.DELETED);
                corporateResourceRepository.save(resource);

                log.info("Corporate Resource {} deleted from R2", resource.getId());

            } catch (Exception e) {
                log.warn("Failed to delete corporate resource {} ({}): {}", resource.getId(), resource.getObjectKey(), e.getMessage());
            }
        }
    }

    // For onboarding documents of commercials
    @Transactional
    @Scheduled(cron = "0 0 * * * *") // every hour
    public void cleanupCommercialDocuments() {

        ZonedDateTime threshold = ZonedDateTime.now().minusHours(maxAgeHours);

        // Solo PENDING nunca confirmados: los descartados por el usuario ya se borran al
        // descartar (CommercialDocumentServiceImpl#discard) y los VALIDATED están en uso.
        List<CommercialDocument> documents = commercialDocumentRepository
                .findByStatusAndUploadedAtBefore(CommercialDocumentStatus.PENDING, threshold);
        log.info("Cleanup job: {} candidate pending commercial documents", documents.size());

        for (CommercialDocument document : documents) {
            try {
                r2Service.deleteObject("private/" + document.getObjectKey());
                document.setStatus(CommercialDocumentStatus.ORPHANED);
                commercialDocumentRepository.save(document);

                log.info("Commercial Document {} (never confirmed) deleted from R2", document.getId());

            } catch (Exception e) {
                log.warn("Failed to delete commercial document {} ({}): {}", document.getId(), document.getObjectKey(), e.getMessage());
            }
        }
    }

    // For legal documents (terms, privacy policy...)
    @Transactional
    @Scheduled(cron = "0 0 * * * *") // every hour
    public void cleanupLegalDocuments() {

        ZonedDateTime threshold = ZonedDateTime.now().minusHours(maxAgeHours);

        // Solo PENDING nunca confirmados (mismo efecto que LegalDocumentServiceImpl#discardUpload).
        // Las versiones publicadas las gestiona la regla de retención al confirmar otra.
        List<LegalDocument> documents = legalDocumentRepository
                .findByStatusAndCreatedAtBefore(LegalDocumentStatus.PENDING, threshold);
        log.info("Cleanup job: {} candidate pending legal documents", documents.size());

        for (LegalDocument document : documents) {
            try {
                // Se sube con isPrivate=false (LegalDocumentServiceImpl#prepareUpload).
                r2Service.deleteObject("public/" + document.getObjectKey());
                legalDocumentRepository.delete(document);

                log.info("Legal Document {} (never confirmed) deleted from R2", document.getId());

            } catch (Exception e) {
                log.warn("Failed to delete legal document {} ({}): {}", document.getId(), document.getObjectKey(), e.getMessage());
            }
        }
    }

    /**
     * Paso 1 de cada barrido: pasa a ORPHANED los assets vencidos que nunca se vincularon
     * ({@code stale}, ya filtrados por el repositorio) para que el paso 2 los borre de R2.
     */
    private <T> void orphanStale(String label, List<T> stale, Function<T, Long> idOf,
            Consumer<T> markOrphaned, Consumer<List<T>> saveAll) {
        if (stale.isEmpty()) {
            return;
        }
        for (T asset : stale) {
            markOrphaned.accept(asset);
            log.info("{} {} orphaned: subida sin vincular y más vieja que {}h", label, idOf.apply(asset), maxAgeHours);
        }
        saveAll.accept(stale);
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
