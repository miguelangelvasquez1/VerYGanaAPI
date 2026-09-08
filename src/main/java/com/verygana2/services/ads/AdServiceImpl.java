package com.verygana2.services.ads;

import java.time.Clock;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.verygana2.dtos.FileUploadPermissionDTO;
import com.verygana2.dtos.FileUploadRequestDTO;
import com.verygana2.dtos.PagedResponse;
import com.verygana2.dtos.ad.requests.AdFilterDTO;
import com.verygana2.dtos.ad.requests.AdUpdateDTO;
import com.verygana2.dtos.ad.requests.CreateAdRequestDTO;
import com.verygana2.dtos.ad.responses.AdAssetUploadPermissionDTO;
import com.verygana2.dtos.ad.responses.AdForAdminDTO;
import com.verygana2.dtos.ad.responses.AdResponseDTO;
import com.verygana2.dtos.ad.responses.AdStatsDTO;
import com.verygana2.dtos.ad.responses.AssetAnalysisResultDTO;
import com.verygana2.dtos.ad.responses.AssetOrphanedResponseDTO;
import com.verygana2.exceptions.adsExceptions.AdNotFoundException;
import com.verygana2.exceptions.adsExceptions.InsufficientBudgetException;
import com.verygana2.exceptions.adsExceptions.InvalidAdStateException;
import com.verygana2.mappers.AdMapper;
import com.verygana2.models.Category;
import com.verygana2.models.Municipality;
import com.verygana2.models.TargetAudience;
import com.verygana2.models.enums.TargetGender;
import com.verygana2.models.PricingConfig;
import com.verygana2.models.ads.Ad;
import com.verygana2.models.ads.AdAsset;
import com.verygana2.models.finance.Wallet;
import com.verygana2.models.enums.AdStatus;
import com.verygana2.models.enums.AssetStatus;
import com.verygana2.models.enums.MediaType;
import com.verygana2.models.enums.SupportedMimeType;
import com.verygana2.models.finance.plans.RequirePlanCapability;
import com.verygana2.models.userDetails.CommercialDetails;
import com.verygana2.repositories.AdAssetRepository;
import com.verygana2.repositories.AdRepository;
import com.verygana2.repositories.WalletRepository;
import com.verygana2.repositories.details.CommercialDetailsRepository;
import com.verygana2.services.PricingConfigService;
import com.verygana2.services.interfaces.AdService;
import com.verygana2.services.interfaces.CategoryService;
import com.verygana2.services.interfaces.NotificationService;
import com.verygana2.storage.service.AssetOrphanedService;
import com.verygana2.storage.service.R2Service;
import com.verygana2.utils.concurrency.RetryOnConcurrencyConflict;
import com.verygana2.utils.specifications.AdSpecifications;
import com.verygana2.utils.validators.AssetDurationService;
import com.verygana2.utils.validators.TargetingValidator;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.PersistenceContext;
import jakarta.validation.ValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class AdServiceImpl implements AdService {

    @PersistenceContext
    private EntityManager entityManager;

    private final AdRepository adRepository;
    private final CommercialDetailsRepository commercialDetailsRepository;
    private final WalletRepository walletRepository;
    private final AdMapper adMapper;
    private final CategoryService categoryService;
    private final TargetingValidator targetingValidator;
    private final Clock clock;
    private final R2Service r2Service;
    private final AdAssetRepository adAssetRepository;
    private final AdAssetAnalysisTx adAssetAnalysisTx;
    private final AssetOrphanedService assetOrphanedService;
    private final AssetDurationService mediaMetadataService;
    private final PricingConfigService pricingConfigService;
    private final NotificationService notificationService;

    /**
     * Ventana de validez de una cotización de análisis. Se reutiliza el TTL del job
     * de limpieza: pasado ese tiempo el asset es candidato a huérfano, así que su
     * precio congelado ya no debe aceptarse en {@link #createAdWithAsset}.
     */
    @Value("${cleanup.orphaned-assets.max-age-hours:24}")
    private int assetQuoteMaxAgeHours;

    // ==================== Consultas para Anunciantes ====================

    // ─────────────────────────────────────────────────────────────────────────
    // STEP 1 — Prepare: create asset record + return R2 upload URL
    // ─────────────────────────────────────────────────────────────────────────
 
    /**
     * Creates an AdAsset in PENDING state and returns a pre-signed R2 URL.
     *
     * For IMAGE: stores imageDurationSeconds on the asset so the analyze
     * endpoint can use it without the frontend re-sending it.
     * For VIDEO: imageDurationSeconds must be null (duration resolved by ffprobe).
     *
     * No pricing info is returned here — that comes after the file is in R2.
     */
    @Transactional
    @RequirePlanCapability({RequirePlanCapability.Capability.CAN_ADVERTISE, RequirePlanCapability.Capability.MAX_ADS})
    public AdAssetUploadPermissionDTO prepareAdAssetUpload(Long commercialId, FileUploadRequestDTO request) {
 
        commercialDetailsRepository
                .findById(Objects.requireNonNull(commercialId))
                .orElseThrow(() -> new EntityNotFoundException("Anunciante no encontrado: " + commercialId));
 
        MediaType mediaType = determineMediaType(request.getContentType());
        validateFileMetadata(request, mediaType);

        Boolean isImage = mediaType == MediaType.IMAGE;
 
        // Validate durationSeconds for IMAGE (required) and VIDEO (must be null)
        validateDurationSeconds(isImage, request.getImageDurationSeconds());
 
        String objectKey = generateAdAssetObjectKey(commercialId, request);
 
        AdAsset asset = AdAsset.builder()
                .objectKey(objectKey)
                .sizeBytes(request.getSizeBytes())
                .mediaType(mediaType)
                .status(AssetStatus.PENDING)
                .uploadedAt(ZonedDateTime.now(clock))
                // Stored now; used in analyze step so frontend doesn't re-send it
                .durationSeconds(isImage ? request.getImageDurationSeconds() : null) // resolved in analyze step
                .ad(null)
                .build();
 
        AdAsset savedAsset = adAssetRepository.save(asset);
 
        FileUploadPermissionDTO permission = r2Service.generateUploadUrl(
                true, objectKey, request.getContentType());
 
        return AdAssetUploadPermissionDTO.builder()
                .assetId(savedAsset.getId())
                .permission(permission)
                .build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // STEP 2 — Analyze: resolve real duration + calculate pricing
    // ─────────────────────────────────────────────────────────────────────────
 
    /**
     * Called AFTER the frontend confirms the file was uploaded to R2.
     *
     * For VIDEO: calls ffprobe via VideoAnalysisService to get the real duration.
     * For IMAGE: uses the imageDurationSeconds stored in step 1.
     *
     * Persists durationSeconds and transitions the asset to VALIDATED.
     * If anything goes wrong, marks the asset as ORPHANED so the cleanup
     * job can remove it from R2 and the database.
     *
     * Returns durationSeconds + minPricePerView so the frontend can show
     * the pricing panel to the advertiser.
     *
     * NOT_SUPPORTED: la validación en R2 y el ffprobe pueden tardar segundos y
     * no deben mantener abierta una transacción ni una conexión del pool. Las
     * únicas escrituras (marcar ANALYZING / VALIDATED) van en transacciones
     * cortas propias vía {@link AdAssetAnalysisTx}.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @RequirePlanCapability({RequirePlanCapability.Capability.CAN_ADVERTISE, RequirePlanCapability.Capability.MAX_ADS})
    public AssetAnalysisResultDTO analyzeAsset(Long assetId, Long commercialId) {

        // Fase 1 (tx corta): valida propiedad y estado, y marca ANALYZING. Tanto
        // "no es tuyo" como "no está PENDING" salen por acá sin disparar el
        // orphaning del catch.
        AdAssetAnalysisTx.AnalysisContext ctx = adAssetAnalysisTx.begin(Objects.requireNonNull(assetId), commercialId);

        try {
            // Fase 2 (SIN transacción): validar mime y tamaño en R2, y resolver duración real.
            Set<SupportedMimeType> allowedMimeTypes = getAllowedMimeTypesForMedia(ctx.mediaType());
            long maxSizeBytes = getMaxSizeBytesForMedia(ctx.mediaType());

            SupportedMimeType realMimeType = r2Service.validateUploadedObject(
                    true,
                    ctx.objectKey(),
                    ctx.sizeBytes(),
                    maxSizeBytes,
                    allowedMimeTypes);

            double durationSeconds = resolveDuration(ctx);
            // Segundos facturables: se redondea hacia arriba y se persiste tal cual,
            // para que la re-validación en createAdWithAsset use exactamente el mismo
            // valor con el que aquí se cotiza el mínimo al anunciante.
            int billableSeconds = (int) Math.ceil(durationSeconds);

            long costPerSecondCents = pricingConfigService.getCurrentValue(PricingConfig.PricingType.AD_COST_PER_SECOND_CENTS);
            long minPricePerLike = minPricePerLikeCents(billableSeconds, costPerSecondCents);

            log.info("Asset {} analyzed: durationSeconds={}, billableSeconds={}, minPricePerLike={}",
                    assetId, durationSeconds, billableSeconds, minPricePerLike);

            // Fase 3 (tx corta): persistir VALIDATED + congelar el mínimo cotizado.
            adAssetAnalysisTx.complete(assetId, realMimeType, billableSeconds, minPricePerLike);

            return AssetAnalysisResultDTO.builder()
                    .durationSeconds(durationSeconds)
                    .minPricePerLike(minPricePerLike)
                    .build();

        } catch (Exception e) {
            log.error("Analysis failed for asset {}. Marking as orphaned. Reason: {}", assetId, e.getMessage(), e);
            assetOrphanedService.markAdAssetsAsOrphanedByIds(List.of(assetId));
            throw new ValidationException("No se pudo analizar el archivo. Verifica que el formato sea compatible y vuelve a intentarlo.");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // STEP 2.5 — Orphan: called when user cancels or changes file
    // ─────────────────────────────────────────────────────────────────────────
 
    /**
     * Marks a PENDING or VALIDATED asset as ORPHANED.
     *
     * Called when:
     *  - User changes the selected file (old asset unused)
     *  - User cancels the form explicitly
     *  - Browser fires beforeunload with a pending assetId (via sendBeacon)
     */
    @Transactional
    @Override
    @RequirePlanCapability({RequirePlanCapability.Capability.CAN_ADVERTISE})
    public AssetOrphanedResponseDTO markAssetAsOrphaned(Long commercialId, Long assetId) {

        AdAsset asset = adAssetRepository.findById(Objects.requireNonNull(assetId))
                .orElseThrow(() -> new EntityNotFoundException("Asset no encontrado: " + assetId));

        // Propiedad: un asset aún sin vincular solo lleva el prefijo commercial-{id}
        // en su objectKey. Sin esta comprobación otro anunciante podría orfanarlo
        // conociendo su id y condenarlo a borrado por el job de limpieza. Se
        // responde "no encontrado" para no revelar la existencia de ids ajenos.
        if (!asset.isOwnedBy(commercialId)) {
            throw new EntityNotFoundException("Asset no encontrado: " + assetId);
        }

        if (asset.getAd() != null) {
            throw new ValidationException("El asset ya está vinculado a un anuncio y no puede ser marcado como huérfano");
        }
 
        if (asset.getStatus() == AssetStatus.ORPHANED) {
            return AssetOrphanedResponseDTO.builder()
                    .assetId(assetId)
                    .message("El asset ya estaba marcado como huérfano")
                    .build();
        }
 
        assetOrphanedService.markAdAssetsAsOrphanedByIds(List.of(assetId));
        log.info("Asset {} orphaned by commercial {}", assetId, commercialId);
 
        return AssetOrphanedResponseDTO.builder()
                .assetId(assetId)
                .message("Asset marcado como huérfano correctamente")
                .build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // STEP 3 — Create: attach asset to ad, deduct budget
    // ─────────────────────────────────────────────────────────────────────────
 
    /**
     * Creates the Ad entity after the advertiser has confirmed pricing.
     *
     * Validates:
     *  - Asset is VALIDATED (analyze succeeded)
     *  - pricePerLike is a multiple of 10 (enforced by CreateAdRequestDTO)
     *  - pricePerLike >= the minPricePerLike frozen on the asset at analyze time
     *  - Advertiser has sufficient wallet balance
     *
     * On any failure, marks the asset as ORPHANED.
     */
    @Transactional
    @RequirePlanCapability(value = {RequirePlanCapability.Capability.CAN_ADVERTISE, RequirePlanCapability.Capability.MAX_ADS}, requiresBudget = true)
    @RetryOnConcurrencyConflict
    public void createAdWithAsset(Long commercialId, CreateAdRequestDTO request) {
 
        // Carga + verificación de propiedad ANTES del try: si fallaran dentro, el
        // catch marcaría huérfano el asset. Un anunciante no debe poder condenar
        // (ni adjuntarse) el asset en subida de otro conociendo su id; el prefijo
        // commercial-{id} del objectKey es la señal de propiedad hasta el vínculo.
        AdAsset asset = adAssetRepository
                .findById(Objects.requireNonNull(request.getAssetId()))
                .orElseThrow(() -> new ValidationException("Asset no encontrado: " + request.getAssetId()));
        if (!asset.isOwnedBy(commercialId)) {
            throw new ValidationException("Asset no encontrado: " + request.getAssetId());
        }

        try {
            if (asset.getAd() != null) {
                throw new ValidationException("Asset ya está asociado a un anuncio: " + asset.getId());
            }
 
            // Must be VALIDATED — analyze must have run successfully
            if (asset.getStatus() != AssetStatus.VALIDATED) {
                throw new ValidationException("El archivo no ha sido analizado correctamente. Estado actual: " + asset.getStatus());
            }

            // Cotización vencida: el mínimo congelado pudo quedar desalineado con
            // AD_COST_PER_SECOND_CENTS. Se rechaza (y el catch marca el asset huérfano)
            // para forzar re-subida + nuevo análisis con precio actualizado.
            if (asset.getUploadedAt().isBefore(ZonedDateTime.now(clock).minusHours(assetQuoteMaxAgeHours))) {
                throw new ValidationException(
                        "El análisis del archivo expiró. Vuelve a subir el archivo para obtener un precio actualizado.");
            }

            // Re-valida pricePerLike contra el mínimo CONGELADO en el análisis, no
            // contra un recálculo: si un admin cambia AD_COST_PER_SECOND_CENTS entre
            // el analyze y este POST, el anunciante no recibe un rechazo inesperado.
            // Fallback (asset analizado antes de introducir el campo): recalcular con
            // la config actual usando la misma fórmula que analyzeAsset.
            long minPricePerLike;
            if (asset.getMinPricePerLikeCents() != null) {
                minPricePerLike = asset.getMinPricePerLikeCents();
            } else {
                long costPerSecondCents = pricingConfigService.getCurrentValue(PricingConfig.PricingType.AD_COST_PER_SECOND_CENTS);
                minPricePerLike = minPricePerLikeCents(asset.getDurationSeconds(), costPerSecondCents);
            }
 
            long pricePerLike = request.getPricePerLike();

            if (pricePerLike < minPricePerLike) {
                throw new ValidationException(String.format(
                        "El precio por like (%d ¢) es menor al mínimo permitido (%d ¢)",
                        pricePerLike, minPricePerLike));
            }
 
            List<Category> categories = categoryService.getValidatedCategories(request.getCategoryIds());
 
            List<Municipality> municipalities = Collections.emptyList();
            if (request.getTargetMunicipalitiesCodes() != null && !request.getTargetMunicipalitiesCodes().isEmpty()) {
                municipalities = targetingValidator.getValidatedMunicipalities(request.getTargetMunicipalitiesCodes());
            }
 
            long totalBudgetCents = pricePerLike * request.getMaxLikes().longValue();

            Wallet wallet = walletRepository.findByCommercialIdForUpdate(commercialId)
                    .orElseThrow(() -> new EntityNotFoundException("Wallet del anunciante no encontrado"));

            wallet.consume(totalBudgetCents);
            walletRepository.save(wallet);
 
            CommercialDetails commercialDetails = entityManager.getReference(CommercialDetails.class, commercialId);
 
            Ad ad = adMapper.toEntity(request, commercialDetails);
            ad.setRewardPerLike(pricePerLike);

            TargetAudience targetAudience = TargetAudience.builder()
                    .categories(categories)
                    .targetMunicipalities(municipalities)
                    .minAge(request.getMinAge())
                    .maxAge(request.getMaxAge())
                    .targetGender(request.getTargetGender() != null ? TargetGender.valueOf(request.getTargetGender()) : null)
                    .build();
            ad.setTargetAudience(targetAudience);
 
            Ad savedAd = adRepository.save(ad);
 
            asset.setAd(savedAd);
            adAssetRepository.save(asset);
 
            log.info("Ad {} created successfully for commercial {}. Budget: {} ¢", savedAd.getId(), commercialId, totalBudgetCents);
 
        } catch (TransientDataAccessException e) {
            // Conflicto de concurrencia transitorio (deadlock 1213, lock-wait 1205,
            // lock optimista sobre la wallet). @RetryOnConcurrencyConflict reintenta
            // con una transacción nueva; el asset NO se marca huérfano porque el
            // próximo intento lo necesita todavía en estado VALIDATED.
            throw e;
        } catch (Exception e) {
            if (asset != null) {
                log.error("Error creating ad, orphaning asset {}: {}", asset.getId(), e.getMessage());
                assetOrphanedService.markAdAssetsAsOrphanedByIds(List.of(asset.getId()));
            }
            throw e;
        }
    }

    @Override
    @Transactional
    @RequirePlanCapability(value = {RequirePlanCapability.Capability.CAN_ADVERTISE}, blockWhenDormant = true)
    public AdResponseDTO updateAd(Long adId, AdUpdateDTO updateDto, Long commercialId) {
        log.info("Updating ad {} for commercial {}", adId, commercialId);

        Ad ad = adRepository.findByIdAndCommercialId(adId, commercialId)
            .orElseThrow(() -> new AdNotFoundException("Anuncio no encontrado"));

        // Solo PENDING o PAUSED pueden editarse
        if (ad.getStatus() != AdStatus.PENDING && ad.getStatus() != AdStatus.PAUSED) {
            throw new InvalidAdStateException(
                "Solo se pueden editar anuncios en estado PENDING o PAUSED"
            );
        }

        List<Category> selectedCategories = categoryService.getValidatedCategories(updateDto.getCategoryIds());

        TargetAudience ta = ad.getTargetAudience();
        if (ta == null) {
            ta = new TargetAudience();
            ad.setTargetAudience(ta);
        }
        ta.setCategories(selectedCategories);

        if (updateDto.getTargetMunicipalitiesCodes() != null) {
            ta.setTargetMunicipalities(updateDto.getTargetMunicipalitiesCodes().isEmpty()
                    ? new ArrayList<>()
                    : targetingValidator.getValidatedMunicipalities(updateDto.getTargetMunicipalitiesCodes()));
        }

        if (updateDto.getMinAge() != null) ta.setMinAge(updateDto.getMinAge());
        if (updateDto.getMaxAge() != null) ta.setMaxAge(updateDto.getMaxAge());
        if (updateDto.getTargetGender() != null) ta.setTargetGender(TargetGender.valueOf(updateDto.getTargetGender()));

        adMapper.updateEntityFromDto(updateDto, ad);

        Ad updatedAd = adRepository.save(ad);

        AdResponseDTO responseDto = adMapper.toDto(updatedAd);
        responseDto.setContentUrl(resolveContentUrl(updatedAd));

        log.info("Ad {} updated successfully by commercial {}", adId, commercialId);
        return responseDto;
    }

    @Override
    @Transactional(readOnly = true)
    @RequirePlanCapability({RequirePlanCapability.Capability.CAN_ADVERTISE})
    public PagedResponse<AdResponseDTO> getFilteredAds(Long commercialId, AdFilterDTO filters, Pageable pageable) {

        Specification<Ad> spec = AdSpecifications.hasCommercial(commercialId)
                .and(AdSpecifications.hasStatus(filters.getStatus()))
                .and(AdSpecifications.hasSearchTerm(filters.getSearchTerm()))
                .and(AdSpecifications.inDateRange(filters.getStartDate(), filters.getEndDate()))
                .and(AdSpecifications.inCategories(filters.getCategoryIds()));

        Sort sort = Sort.by(Sort.Direction.DESC, "createdAt");

        Pageable fixedSortPageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);
        Page<Ad> adsPage = adRepository.findAll(spec, fixedSortPageable);

        Page<AdResponseDTO> dtoPage = adsPage.map(ad -> {
            AdResponseDTO dto = adMapper.toDto(ad);

            AdAsset asset = ad.getAsset();
            dto.setMediaType(asset != null ? asset.getMediaType() : null);
            if (asset == null) {
                dto.setContentUrl(null);
                return dto;
            }

            dto.setContentUrl(resolveContentUrl(ad));
            return dto;
        });

        return PagedResponse.from(dtoPage);
    }

    @Override
    public AdResponseDTO getAdDetails(Long adId, Long commercialId) {
        Ad ad = adRepository.findByIdAndCommercialId(adId, commercialId)
            .orElseThrow(() -> new AdNotFoundException("Anuncio no encontrado"));

        AdResponseDTO dto = adMapper.toDto(ad);
        dto.setContentUrl(
            r2Service.getPrivateObject(ad.getAsset().getObjectKey(), 200)
        );

        return dto;
    }

    @Override
    @Transactional(readOnly = true)
    public Ad getAdEntityById(Long adId) {
        Objects.requireNonNull(adId, "El ID del anuncio no puede ser nulo");
        return adRepository.findById(adId)
                .orElseThrow(() -> new AdNotFoundException("Anuncio no encontrado con ID: " + adId));
    }

    @Override
    @RequirePlanCapability({RequirePlanCapability.Capability.CAN_ADVERTISE, RequirePlanCapability.Capability.MAX_ADS})
    public AdResponseDTO activateAdAsCommercial(Long adId, Long commercialId) {
        Ad ad = adRepository.findByIdAndCommercialId(adId, commercialId)
                .orElseThrow(() -> new AdNotFoundException("Anuncio no encontrado"));

        if (ad.getStatus() != AdStatus.APPROVED && ad.getStatus() != AdStatus.PAUSED) {
            throw new InvalidAdStateException("Solo se pueden activar anuncios aprobados o pausados");
        }

        ad.setStatus(AdStatus.ACTIVE);
        ad.setUpdatedAt(ZonedDateTime.now(clock));

        if (ad.getStartDate() == null) {
            ad.setStartDate(ZonedDateTime.now(clock));
        }

        Ad savedAd = adRepository.save(ad);
        log.info("Ad {} activated", adId);

        AdResponseDTO responseDto = adMapper.toDto(savedAd);
        responseDto.setContentUrl(resolveContentUrl(savedAd));

        return responseDto;
    }

    @Override
    @RequirePlanCapability({RequirePlanCapability.Capability.CAN_ADVERTISE})
    public AdResponseDTO pauseAdAsCommercial(Long adId, Long commercialId) {
        Ad ad = adRepository.findByIdAndCommercialId(adId, commercialId)
                .orElseThrow(() -> new AdNotFoundException("Anuncio no encontrado"));

        if (ad.getStatus() != AdStatus.ACTIVE) {
            throw new InvalidAdStateException("Solo se pueden pausar anuncios activos");
        }

        ad.setStatus(AdStatus.PAUSED);
        ad.setUpdatedAt(ZonedDateTime.now(clock));

        Ad savedAd = adRepository.save(ad);
        log.info("Ad {} paused", adId);

        AdResponseDTO responseDto = adMapper.toDto(savedAd);
        responseDto.setContentUrl(resolveContentUrl(savedAd));

        return responseDto;
    }

    // ==================== Gestión de Estado (Admin) ====================

    @Override
    public AdResponseDTO activateAdAsAdmin(Long adId) {
        Objects.requireNonNull(adId, "El ID del anuncio no puede ser nulo");
        Ad ad = adRepository.findById(adId)
                .orElseThrow(() -> new AdNotFoundException("Anuncio no encontrado"));

        if (ad.getStatus() != AdStatus.APPROVED && ad.getStatus() != AdStatus.PAUSED
                && ad.getStatus() != AdStatus.BLOCKED) {
            throw new InvalidAdStateException(
                    "Solo se pueden activar anuncios aprobados o pausados o bloqueados");
        }

        ad.setStatus(AdStatus.ACTIVE);
        ad.setUpdatedAt(ZonedDateTime.now(clock));

        if (ad.getStartDate() == null) {
            ad.setStartDate(ZonedDateTime.now(clock));
        }

        Ad savedAd = adRepository.save(ad);
        log.info("Ad {} activated", adId);

        AdResponseDTO responseDto = adMapper.toDto(savedAd);
        responseDto.setContentUrl(resolveContentUrl(savedAd));

        return responseDto;
    }

    @Override
    public AdResponseDTO pauseAdAsAdmin(Long adId) {
        Objects.requireNonNull(adId, "El ID del anuncio no puede ser nulo");
        Ad ad = adRepository.findById(adId)
                .orElseThrow(() -> new AdNotFoundException("Anuncio no encontrado"));

        if (ad.getStatus() != AdStatus.ACTIVE && ad.getStatus() != AdStatus.BLOCKED) {
            throw new InvalidAdStateException(
                    "Solo se pueden pausar anuncios activos o bloqueados");
        }

        ad.setStatus(AdStatus.PAUSED);
        ad.setUpdatedAt(ZonedDateTime.now(clock));

        Ad savedAd = adRepository.save(ad);
        log.info("Ad {} paused", adId);

        AdResponseDTO responseDto = adMapper.toDto(savedAd);
        responseDto.setContentUrl(resolveContentUrl(savedAd));

        return responseDto;
    }

    @Override
    public AdResponseDTO blockAdAsAdmin(Long adId) {
        Objects.requireNonNull(adId, "El ID del anuncio no puede ser nulo");
        Ad ad = adRepository.findById(adId)
                .orElseThrow(() -> new AdNotFoundException("Anuncio no encontrado"));

        if (ad.getStatus() != AdStatus.APPROVED && ad.getStatus() != AdStatus.PAUSED
                && ad.getStatus() != AdStatus.ACTIVE) {
            throw new InvalidAdStateException(
                    "Solo se pueden bloquear anuncios activos, pausados o aprobados");
        }

        ad.setStatus(AdStatus.BLOCKED);
        ad.setUpdatedAt(ZonedDateTime.now(clock));

        Ad savedAd = adRepository.save(ad);
        log.info("Ad {} blocked", adId);

        // No se reembolsa: el anuncio puede ser reactivado por un admin (activateAdAsAdmin),
        // así que el presupuesto restante se mantiene reservado en el anuncio.
        notificationService.createInternalNotification(
                savedAd.getCommercial().getUser().getId(),
                "Anuncio bloqueado",
                "Tu anuncio \"" + savedAd.getTitle() + "\" fue bloqueado por un administrador",
                Instant.now());

        AdResponseDTO responseDto = adMapper.toDto(savedAd);
        responseDto.setContentUrl(resolveContentUrl(savedAd));

        return responseDto;
    }

    @Override
    public AdResponseDTO approveAd(Long adId, Long adminId) {
        log.info("Admin {} approving ad {}", adminId, adId);

        Ad ad = getAdEntityById(adId);

        if (ad.getStatus() != AdStatus.PENDING) {
            throw new InvalidAdStateException("Solo se pueden aprobar anuncios pendientes");
        }

        r2Service.makeObjectPublic(ad.getAsset().getObjectKey());

        ad.setStatus(AdStatus.APPROVED);
        ad.setUpdatedAt(ZonedDateTime.now(clock));

        Ad savedAd = adRepository.save(ad);
        log.info("Ad {} approved successfully", adId);

        AdResponseDTO responseDto = adMapper.toDto(savedAd);
        responseDto.setContentUrl(resolveContentUrl(savedAd));

        return responseDto;
    }

    @Override
    public AdResponseDTO rejectAd(Long adId, String reason, Long adminId) {
        log.info("Admin {} rejecting ad {}", adminId, adId);

        Ad ad = getAdEntityById(adId);

        if (ad.getStatus() != AdStatus.PENDING) {
            throw new InvalidAdStateException(
                    "Solo se pueden rechazar anuncios pendientes");
        }

        ad.setStatus(AdStatus.REJECTED);
        ad.setRejectionReason(reason);
        ad.setUpdatedAt(ZonedDateTime.now(clock));

        Ad savedAd = adRepository.save(ad);
        log.info("Ad {} rejected", adId);

        refundRemainingBudget(savedAd);
        notificationService.createInternalNotification(
                savedAd.getCommercial().getUser().getId(),
                "Anuncio rechazado",
                "Tu anuncio \"" + savedAd.getTitle() + "\" fue rechazado"
                        + (reason != null && !reason.isBlank() ? ": " + reason : ""),
                Instant.now());

        AdResponseDTO responseDto = adMapper.toDto(savedAd);
        responseDto.setContentUrl(resolveContentUrl(savedAd));

        return responseDto;
    }

    // Get all ads for admin
    @Override
    @Transactional(readOnly = true)
    public Page<AdForAdminDTO> getAdsByStatus(AdStatus status, Pageable pageable) {
        Page<Ad> ads = adRepository.findAllByStatus(status, pageable);

        return ads.map(ad -> {
            AdForAdminDTO dto = adMapper.toAdminDto(ad);

            AdAsset asset = ad.getAsset();
            dto.setMediaType(asset != null ? asset.getMediaType() : null);

            if (asset == null) {
                dto.setContentUrl(null);
                return dto;
            }

            dto.setContentUrl(resolveContentUrl(ad));

            return dto;
        });
    }

    // ==================== Estadísticas ====================

    @Override
    @Transactional(readOnly = true)
    public AdStatsDTO getCommercialStats(Long commercialId) {
        Long totalAds = countAdsByCommercial(commercialId);
        Long activeAds = countAdsByCommercialAndStatus(commercialId, AdStatus.ACTIVE);
        // BigDecimal totalSpent = getTotalSpentByCommercial(commercialId);
        Long totalLikes = getTotalLikesByCommercial(commercialId);

        return AdStatsDTO.builder()
                .totalAds(totalAds.intValue())
                .activeAds(activeAds.intValue())
                // .totalSpent(totalSpent)
                .totalLikesReceived(totalLikes)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AdResponseDTO> getTopAdsByLikes(Pageable pageable) {
        Page<Ad> ads = adRepository.findTopAdsByLikes(pageable);
        return ads.map(adMapper::toDto);
    }

    // ==================== Validaciones ====================

    @Override
    public void validateAdBudget(Long adId) {
        Ad ad = getAdEntityById(adId);

        if (!ad.hasRemainingBudget()) {
            ad.setStatus(AdStatus.COMPLETED);
            adRepository.save(ad);

            throw new InsufficientBudgetException(
                    "El anuncio ha agotado su presupuesto");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public boolean canAdReceiveLike(Long adId) {
        Ad ad = getAdEntityById(adId);
        return ad.canReceiveLike();
    }

    // ==================== Utilidades ====================

    @Override
    @Transactional(readOnly = true)
    public Long countAdsByCommercial(Long commercialId) {
        return adRepository.countByCommercialId(commercialId);
    }

    @Override
    @Transactional(readOnly = true)
    public Long countAdsByCommercialAndStatus(Long commercialId, AdStatus status) {
        return adRepository.countByCommercialIdAndStatus(commercialId, status);
    }

    // @Override
    // @Transactional(readOnly = true)
    // public BigDecimal getTotalSpentByCommercial(Long commercialId) {
    // BigDecimal total = adRepository.sumSpentBudgetByCommercialId(commercialId);
    // return total != null ? total : BigDecimal.ZERO;
    // }

    @Override
    @Transactional(readOnly = true)
    public Long getTotalLikesByCommercial(Long commercialId) {
        Long total = adRepository.sumLikesByCommercialId(commercialId);
        return total != null ? total : 0L;
    }

    /**
     * Devuelve el presupuesto no consumido del anuncio a la wallet del anunciante.
     * Solo se usa al rechazar un anuncio (rechazo = terminal). Al bloquear NO se reembolsa,
     * porque un admin puede reactivar el anuncio más adelante.
     */
    private void refundRemainingBudget(Ad ad) {
        long remaining = ad.getRemainingBudget();
        if (remaining <= 0) {
            return;
        }

        Wallet wallet = walletRepository.findByCommercialIdForUpdate(ad.getCommercial().getId())
                .orElseThrow(() -> new EntityNotFoundException("Wallet del anunciante no encontrado"));

        wallet.deposit(remaining);
        walletRepository.save(wallet);
        log.info("Refunded {} ¢ to commercial {} for ad {}", remaining, ad.getCommercial().getId(), ad.getId());
    }

    // Métodos privados auxiliares -----------------------------

    /**
     * Determinar tipo de media según content-type
     */
    private MediaType determineMediaType(String contentType) {
        if (contentType.startsWith("image/")) {
            return MediaType.IMAGE;
        } else if (contentType.startsWith("video/")) {
            return MediaType.VIDEO;
        } else {
            throw new ValidationException("Content type no soportado: " + contentType);
        }
    }

    /**
     * Validar metadata del archivo
     */
    private void validateFileMetadata(FileUploadRequestDTO metadata, MediaType mediaType) {
        // Validar tamaño según tipo de media
        long maxSize = getMaxSizeBytesForMedia(mediaType);

        if (metadata.getSizeBytes() > maxSize) {
            throw new ValidationException(
                    String.format("Archivo muy grande. Máximo permitido para %s: %d MB",
                            mediaType, maxSize / 1024 / 1024));
        }

        // Validar content-type
        if (mediaType == MediaType.IMAGE && !metadata.getContentType().startsWith("image/")) {
            throw new ValidationException("Content type debe ser image/* para media type IMAGE");
        }

        if (mediaType == MediaType.VIDEO && !metadata.getContentType().startsWith("video/")) {
            throw new ValidationException("Content type debe ser video/* para media type VIDEO");
        }
    }

    /**
     * Generar object key único para R2
     */
    private String generateAdAssetObjectKey(Long commercialId, FileUploadRequestDTO metadata) {
        String timestamp = String.valueOf(System.currentTimeMillis());
        String uuid = UUID.randomUUID().toString().substring(0, 8);
        String extension = getFileExtension(metadata.getOriginalFileName());

        // El prefijo se comparte con AdAsset.isOwnedBy: es la señal de propiedad
        // del asset hasta que queda vinculado a un anuncio.
        return AdAsset.objectKeyPrefixFor(commercialId) + timestamp + "-" + uuid + extension;
    }

    /**
     * Obtener extensión del archivo
     */
    private String getFileExtension(String filename) {
        int lastDot = filename.lastIndexOf('.');
        if (lastDot == -1) {
            return "";
        }
        return filename.substring(lastDot);
    }

    /**
     * Obtener mime types permitidos según tipo de media
     */
    private Set<SupportedMimeType> getAllowedMimeTypesForMedia(MediaType mediaType) {
        return switch (mediaType) {
            case IMAGE -> Set.of(
                    SupportedMimeType.IMAGE_JPEG,
                    SupportedMimeType.IMAGE_PNG,
                    SupportedMimeType.IMAGE_WEBP);
            case VIDEO -> Set.of(
                    SupportedMimeType.VIDEO_MP4,
                    SupportedMimeType.VIDEO_QUICK_TIME);
            default -> throw new ValidationException("Media type no soportado: " + mediaType);
        };
    }

    /**
     * Obtener tamaño máximo según tipo de media
     */
    private long getMaxSizeBytesForMedia(MediaType mediaType) {
        return switch (mediaType) {
            case IMAGE -> 5 * 1024 * 1024; // 5 MB
            case VIDEO -> 100 * 1024 * 1024; // 100 MB
            default -> throw new ValidationException("Media type no soportado: " + mediaType);
        };
    }

    private double resolveDuration(AdAssetAnalysisTx.AnalysisContext ctx) {
        if (ctx.mediaType() == MediaType.VIDEO) {
            // Your existing service — throws StorageException / ValidationException on failure
            Double duration = mediaMetadataService.getVideoDurationSeconds(ctx.objectKey());

            if (duration < 5 || duration > 120) {
                throw new ValidationException("Duración de video no permitida: " + duration + "s");
            }
            return duration;
        }

        // IMAGE: use the advertiser-chosen duration stored in step 1
        if (ctx.storedImageDurationSeconds() == null) {
            throw new ValidationException("Duración de imagen no encontrada en el asset. Esto no debería ocurrir.");
        }
        return ctx.storedImageDurationSeconds().doubleValue();
    }

    /**
     * Precio mínimo por like en céntimos.
     *
     * <p>El anunciante solo puede elegir múltiplos de 10 (lo exige
     * {@code CreateAdRequestDTO#isPricePerLikeMultipleOf10}), así que el mínimo
     * real es el primer múltiplo de 10 que cubre
     * {@code billableSeconds * costPerSecondCents}.
     *
     * <p>Debe llamarse con los mismos {@code billableSeconds} en
     * {@link #analyzeAsset} (cotización que ve el anunciante) y en
     * {@link #createAdWithAsset} (re-validación de POST /ads) para que ambas
     * rutas devuelvan el mismo valor.
     */
    private long minPricePerLikeCents(long billableSeconds, long costPerSecondCents) {
        return roundUpToMultipleOf10(billableSeconds * costPerSecondCents);
    }

    /**
     * Rounds a value up to the nearest multiple of 10.
     * Examples: 0→0, 1→10, 10→10, 11→20, 35→40
     */
    private long roundUpToMultipleOf10(long value) {
        if (value <= 0) return 0;
        return (long) (Math.ceil(value / 10.0) * 10);
    }

    private void validateDurationSeconds(Boolean isImage, Integer durationSeconds) {
        // For IMAGE, imageDurationSeconds is required
        if (isImage && durationSeconds == null) {
            throw new ValidationException("La duración de visualización es requerida para anuncios de imagen");
        }
        // For VIDEO, imageDurationSeconds must not be set
        if (!isImage && durationSeconds != null) {
            throw new ValidationException("La duración manual solo aplica para imágenes");
        }
    }

    private String resolveContentUrl(Ad ad) {
        if (ad.getAsset() == null) {
            return null;
        }
        switch (ad.getStatus()) {
            case PENDING:
            case REJECTED:
                return r2Service.getPrivateObject(ad.getAsset().getObjectKey(), 300);
            case APPROVED:
            case ACTIVE:
            case PAUSED:
            case COMPLETED:
            case EXPIRED:
                return r2Service.buildPublicUrl(ad.getAsset().getObjectKey());
            case BLOCKED:
            default:
                return null;
        }
    }
}