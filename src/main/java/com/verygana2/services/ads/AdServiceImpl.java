package com.verygana2.services.ads;

import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.security.access.AccessDeniedException;
import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
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
import com.verygana2.storage.service.AssetOrphanedService;
import com.verygana2.storage.service.R2Service;
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
    private final AssetOrphanedService assetOrphanedService;
    private final AssetDurationService mediaMetadataService;
    private final PricingConfigService pricingConfigService;

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
     */
    @Transactional
    @RequirePlanCapability({RequirePlanCapability.Capability.CAN_ADVERTISE, RequirePlanCapability.Capability.MAX_ADS})
    public AssetAnalysisResultDTO analyzeAsset(Long assetId, Long commercialId) {
 
        AdAsset asset = adAssetRepository
                .findById(Objects.requireNonNull(assetId))
                .orElseThrow(() -> new EntityNotFoundException("Asset no encontrado: " + assetId));
 
        // Security: asset must not be linked to any ad yet
        if (asset.getAd() != null) {
            throw new ValidationException("Asset ya está vinculado a un anuncio");
        }
 
        // Only PENDING assets can be analyzed
        if (asset.getStatus() != AssetStatus.PENDING) {
            throw new ValidationException(
                    "El asset no está en estado válido para analizar. Estado actual: " + asset.getStatus());
        }

        // Mark as ANALYZING so concurrent calls or stale retries are rejected
        asset.setStatus(AssetStatus.ANALYZING);
        adAssetRepository.save(asset);
 
        try {
            // 1. Validar mime y tamaño en R2 PRIMERO — si el archivo es inválido, falla aquí
            MediaType mediaType = asset.getMediaType();
            Set<SupportedMimeType> allowedMimeTypes = getAllowedMimeTypesForMedia(mediaType);
            long maxSizeBytes = getMaxSizeBytesForMedia(mediaType);

            SupportedMimeType realMimeType = r2Service.validateUploadedObject(
                    true,
                    asset.getObjectKey(),
                    asset.getSizeBytes(),
                    maxSizeBytes,
                    allowedMimeTypes);

            asset.setMimeType(realMimeType);

            // 2. Resolver duración real
            double durationSeconds = resolveDuration(asset);

            // 3. Calcular precio mínimo
            long costPerSecondCents = pricingConfigService.getCurrentValue(PricingConfig.PricingType.AD_COST_PER_SECOND_CENTS);
            long rawPrice = (long) Math.ceil(durationSeconds * costPerSecondCents);
            long minPricePerLike = roundUpToMultipleOf10(rawPrice);

            log.info("Asset {} analyzed: durationSeconds={}, minPricePerLike={}",
                    assetId, durationSeconds, minPricePerLike);

            // 4. Persistir todo junto una sola vez
            asset.setDurationSeconds(Double.valueOf(durationSeconds).intValue());
            asset.setStatus(AssetStatus.VALIDATED);
            adAssetRepository.save(asset);

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
 
        if (asset.getAd() != null) {
            Long adCommercialId = asset.getAd().getCommercial().getId();
            if (!adCommercialId.equals(commercialId)) {
                throw new AccessDeniedException("No tienes permiso para modificar este asset");
            }
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
     *  - pricePerLike is a multiple of 10
     *  - pricePerLike >= minPricePerLike (re-calculated from persisted duration)
     *  - Advertiser has sufficient wallet balance
     *
     * On any failure, marks the asset as ORPHANED.
     */
    @Transactional
    @RequirePlanCapability({RequirePlanCapability.Capability.CAN_ADVERTISE, RequirePlanCapability.Capability.MAX_ADS})
    public void createAdWithAsset(Long commercialId, CreateAdRequestDTO request) {
 
        AdAsset asset = null;
 
        try {
            asset = adAssetRepository
                    .findById(Objects.requireNonNull(request.getAssetId()))
                    .orElseThrow(() -> new ValidationException("Asset no encontrado: " + request.getAssetId()));
 
            if (asset.getAd() != null) {
                throw new ValidationException("Asset ya está asociado a un anuncio: " + asset.getId());
            }
 
            // Must be VALIDATED — analyze must have run successfully
            if (asset.getStatus() != AssetStatus.VALIDATED) {
                throw new ValidationException("El archivo no ha sido analizado correctamente. Estado actual: " + asset.getStatus());
            }
 
            // Re-validate pricePerLike server-side against the persisted real duration
            double durationSeconds = asset.getDurationSeconds();
            long costPerSecondCents = pricingConfigService.getCurrentValue(PricingConfig.PricingType.AD_COST_PER_SECOND_CENTS);
            long minPricePerLike = (long) Math.ceil(durationSeconds * costPerSecondCents);
 
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
 
            Wallet wallet = walletRepository.findByCommercialId(commercialId)
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
    @RequirePlanCapability({RequirePlanCapability.Capability.CAN_ADVERTISE})
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
            log.info("ad: " + ad.getRewardPerLike().toString());
            AdResponseDTO dto = adMapper.toDto(ad);

            AdAsset asset = ad.getAsset();
            dto.setMediaType(asset != null ? asset.getMediaType() : null);
            if (asset == null) {
                dto.setContentUrl(null);
                return dto;
            }

            dto.setContentUrl(resolveContentUrl(ad));
            log.info("dto: " + dto.getRewardPerLike().toString());
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
    public AdStatsDTO getAdStats(Long adId, Long commercialId) {
        Ad ad = adRepository.findByIdAndCommercialId(adId, commercialId)
                .orElseThrow(() -> new AdNotFoundException("Anuncio no encontrado"));

        return AdStatsDTO.builder()
                .adId(adId)
                .totalLikes(ad.getCurrentLikes())
                .maxLikes(ad.getMaxLikes())
                .remainingLikes(ad.getRemainingLikes())
                .completionPercentage(ad.getCompletionPercentage())
                .totalBudget(centsToCOP(ad.getTotalBudget()))
                .spentBudget(centsToCOP(ad.getSpentBudget()))
                .remainingBudget(centsToCOP(ad.getRemainingBudget()))
                .rewardPerLike(centsToCOP(ad.getRewardPerLike()))
                .status(ad.getStatus())
                .createdAt(ad.getCreatedAt())
                .startDate(ad.getStartDate())
                .endDate(ad.getEndDate())
                .build();
    }

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

    // ── Helpers de conversión ─────────────────────────────────────────────────

    private static final BigDecimal CENTS_PER_COP = BigDecimal.valueOf(100);

    private BigDecimal centsToCOP(Long cents) {
        if (cents == null) return BigDecimal.ZERO;
        return BigDecimal.valueOf(cents).divide(CENTS_PER_COP, 2, RoundingMode.HALF_UP);
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

        return String.format("ads/commercial-%d/%s-%s%s",
                commercialId, timestamp, uuid, extension);
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

    private double resolveDuration(AdAsset asset) {
        if (asset.getMediaType() == MediaType.VIDEO) {
            // Your existing service — throws StorageException / ValidationException on failure
            Double duration = mediaMetadataService.getVideoDurationSeconds(asset.getObjectKey());

            if (duration < 5 || duration > 120) {
                throw new ValidationException("Duración de video no permitida: " + duration + "s");
            }
            return duration;
        }
 
        // IMAGE: use the advertiser-chosen duration stored in step 1
        if (asset.getDurationSeconds() == null) {
            throw new ValidationException("Duración de imagen no encontrada en el asset. Esto no debería ocurrir.");
        }
        return asset.getDurationSeconds().doubleValue();
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