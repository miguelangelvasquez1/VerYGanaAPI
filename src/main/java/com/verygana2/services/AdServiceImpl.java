package com.verygana2.services;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.hibernate.ObjectNotFoundException;
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
import com.verygana2.dtos.ad.responses.AdForConsumerDTO;
import com.verygana2.dtos.ad.responses.AdResponseDTO;
import com.verygana2.dtos.ad.responses.AdStatsDTO;
import com.verygana2.exceptions.adsExceptions.AdNotFoundException;
import com.verygana2.exceptions.adsExceptions.InsufficientBudgetException;
import com.verygana2.exceptions.adsExceptions.InvalidAdStateException;
import com.verygana2.mappers.AdMapper;
import com.verygana2.models.Category;
import com.verygana2.models.Municipality;
import com.verygana2.models.User;
import com.verygana2.models.ads.Ad;
import com.verygana2.models.ads.AdAsset;
import com.verygana2.models.ads.AdWatchSession;
import com.verygana2.models.enums.AdStatus;
import com.verygana2.models.enums.AdWatchSessionStatus;
import com.verygana2.models.enums.AssetStatus;
import com.verygana2.models.enums.MediaType;
import com.verygana2.models.enums.SupportedMimeType;
import com.verygana2.models.userDetails.AdvertiserDetails;
import com.verygana2.repositories.AdAssetRepository;
import com.verygana2.repositories.AdRepository;
import com.verygana2.repositories.AdWatchSessionRepository;
import com.verygana2.repositories.MunicipalityRepository;
import com.verygana2.repositories.UserRepository;
import com.verygana2.services.interfaces.AdService;
import com.verygana2.services.interfaces.CategoryService;
import com.verygana2.storage.service.AssetOrphanedService;
import com.verygana2.storage.service.R2Service;
import com.verygana2.utils.specifications.AdSpecifications;
import com.verygana2.utils.validators.AssetDurationService;
import com.verygana2.utils.validators.DateValidator;
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
    private final UserRepository userRepository;
    private final AdMapper adMapper;
    private final CategoryService categoryService;
    private final MunicipalityRepository municipalityRepository;
    private final TargetingValidator targetingValidator;
    private final AdWatchSessionRepository adWatchSessionRepository;
    private final Clock clock;
    private final R2Service r2Service;
    private final AdAssetRepository adAssetRepository;
    private final AssetOrphanedService assetOrphanedService;
    private final AssetDurationService mediaMetadataService;

    // ==================== Consultas para Anunciantes ====================

    /**
     * PASO 1: Preparar la subida del asset del anuncio
     * 
     * Este método:
     * - Valida la metadata del archivo
     * - Determina el tipo de media (IMAGE o VIDEO) según content-type
     * - Crea un registro de AdAsset en estado PENDING
     * - Genera una URL pre-firmada de R2
     * - Retorna el assetId y la URL para subir
     * 
     * @param advertiserId ID del anunciante
     * @param request Metadata del archivo
     * @return AssetId y URL pre-firmada
     */
    @Transactional
    public AdAssetUploadPermissionDTO prepareAdAssetUpload(Long advertiserId, FileUploadRequestDTO request) {

        // 1. Validar que el advertiser existe
        userRepository
            .findById(Objects.requireNonNull(advertiserId, "advertiserId must not be null"))
            .orElseThrow(() -> new EntityNotFoundException("Anunciante no encontrado: " + advertiserId));

        // 2. Determinar tipo de media según content-type
        MediaType mediaType = determineMediaType(request.getContentType());

        // 3. Validar metadata del archivo
        validateFileMetadata(request, mediaType);

        // 4. Generar key única en R2
        String objectKey = generateAdAssetObjectKey(advertiserId, request);

        // 5. Crear asset en estado PENDING
        AdAsset asset = AdAsset.builder()
            .objectKey(objectKey)
            .sizeBytes(request.getSizeBytes())
            .mediaType(mediaType)
            .status(AssetStatus.PENDING)
            .uploadedAt(ZonedDateTime.now())
            .ad(null)
            .build();

        AdAsset savedAsset = adAssetRepository.save(Objects.requireNonNull(asset));

        // 6. Generar pre-signed URL de R2
        FileUploadPermissionDTO permission = r2Service.generateUploadUrl(
            objectKey,
            request.getContentType()
        );

        return AdAssetUploadPermissionDTO.builder()
            .assetId(savedAsset.getId())
            .permission(permission)
            .build();
    }

    /**
     * PASO 2: Crear anuncio con asset ya subido
     * 
     * Este método se llama DESPUÉS de que el frontend confirme
     * que la subida a R2 fue exitosa.
     * 
     * Flujo:
     * - Valida que el asset exista y pertenezca al usuario
     * - Valida el archivo subido en R2 (mime type, tamaño)
     * - Valida categorías y ubicaciones
     * - Calcula y valida presupuesto
     * - Crea el anuncio en estado PENDING_APPROVAL
     * 
     * @param advertiserId ID del anunciante
     * @param request Datos del anuncio
     * @return ID del anuncio creado
     */
    @Transactional
    public void createAdWithAsset(Long advertiserId, CreateAdRequestDTO request) {

        AdAsset asset = null;

        try {
            User advertiser = userRepository
                .findById(Objects.requireNonNull(advertiserId))
                .orElseThrow(() -> new EntityNotFoundException("Anunciante no encontrado: " + advertiserId));

            asset = adAssetRepository
                .findById(Objects.requireNonNull(request.getAssetId()))
                .orElseThrow(() -> new ValidationException("Asset no encontrado: " + request.getAssetId()));

            if (asset.getAd() != null) {
                throw new ValidationException("Asset ya está asociado a un anuncio: " + asset.getId());
            }

            if (asset.getStatus() != AssetStatus.PENDING) {
                throw new ValidationException("Asset no está en estado válido para crear anuncio: " + asset.getStatus());
            }

            log.info("Validando archivo en R2: {}", asset.getObjectKey());
            
            MediaType mediaType = MediaType.valueOf(request.getMediaType());
            Set<SupportedMimeType> allowedMimeTypes = getAllowedMimeTypesForMedia(mediaType);
            long maxSizeBytes = getMaxSizeBytesForMedia(mediaType);

            SupportedMimeType realMimeType = r2Service.validateUploadedObject(
                asset.getObjectKey(),
                asset.getSizeBytes(),
                maxSizeBytes,
                allowedMimeTypes
            );

            asset.setMimeType(realMimeType);
            asset.setStatus(AssetStatus.VALIDATED);

            Double durationSeconds = resolveAssetDuration(asset);
            asset.setDurationSeconds(durationSeconds);

            List<Category> categories = categoryService.getValidatedCategories(request.getCategoryIds());

            List<Municipality> municipalities = Collections.emptyList();
            if (request.getTargetMunicipalitiesCodes() != null && !request.getTargetMunicipalitiesCodes().isEmpty()) {
                municipalities = targetingValidator.getValidatedMunicipalities(request.getTargetMunicipalitiesCodes());
            }

            // 9. Calcular presupuesto total
            BigDecimal totalBudget = request.getRewardPerLike()
                .multiply(BigDecimal.valueOf(request.getMaxLikes()));

            // 10. Validar saldo del advertiser
            BigDecimal currentBalance = advertiser.getWallet().getBalance();
            if (currentBalance.compareTo(totalBudget) < 0) {
                throw new ValidationException(
                    String.format("Saldo insuficiente. Requerido: $%s, Disponible: $%s", 
                        totalBudget, currentBalance)
                );
            }

            // 11. Crear anuncio, usar mapper
            AdvertiserDetails advertiserDetails =
                entityManager.getReference(AdvertiserDetails.class, advertiserId);

            Ad ad = adMapper.toEntity(request, advertiserDetails);

            ad.setCategories(categories);
            ad.setTargetMunicipalities(municipalities);

            Ad savedAd = adRepository.save(Objects.requireNonNull(ad));

            // 15. Asociar asset al anuncio
            asset.setAd(savedAd);
            adAssetRepository.save(asset);

        } catch (Exception e) {
            if (asset != null) {
                log.error("Error creando anuncio, marcando asset como huérfano: {}", asset.getId());
                assetOrphanedService.markAdAssetsAsOrphanedByIds(List.of(asset.getId()));
            }
            throw e;
        }
    }

    @Override
    public AdResponseDTO updateAd(Long adId, AdUpdateDTO updateDto, Long advertiserId) {
        log.info("Updating ad {} for advertiser {}", adId, advertiserId);

        DateValidator.validateStartBeforeEnd(updateDto.getStartDate(), updateDto.getEndDate(),
    "La fecha de fin debe ser posterior a la de inicio");
        
        Ad ad = adRepository.findByIdAndAdvertiserId(adId, advertiserId)
            .orElseThrow(() -> new AdNotFoundException("Anuncio no encontrado"));
        
        // Only edit when status is PENDING
        if (ad.getStatus() != AdStatus.PENDING) {
            throw new InvalidAdStateException("Solo se pueden editar anuncios pendientes de aprobación");
        }
        
        List<Category> selectedCategories = categoryService.getValidatedCategories(updateDto.getCategoryIds());
        ad.setCategories(selectedCategories);

        // Validar y actualizar municipios si se proporcionan
        if (updateDto.getTargetMunicipalitiesCodes() != null) {
            List<String> codes = updateDto.getTargetMunicipalitiesCodes();
            List<Municipality> targetMunicipalities = new ArrayList<>();
            
            if (!codes.isEmpty()) {
                targetMunicipalities = municipalityRepository.findAllById(codes);

            }
            
            ad.setTargetMunicipalities(targetMunicipalities);
        }

        adMapper.updateEntityFromDto(updateDto, ad);
        
        Ad updatedAd = adRepository.save(ad);
    
        AdResponseDTO responseDto = adMapper.toDto(updatedAd);
        responseDto.setContentUrl(r2Service.generatePresignedUrl("private/" + updatedAd.getAsset().getObjectKey(), 200));

        log.info("Ad {} updated successfully by advertiser {}", adId, advertiserId);
        return responseDto;
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<AdResponseDTO> getFilteredAds(Long advertiserId, AdFilterDTO filters, Pageable pageable) {

        Specification<Ad> spec = AdSpecifications.hasAdvertiser(advertiserId)
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
            
            switch (ad.getStatus()) {

                case PENDING:
                case REJECTED:
                    // Asset privado → presigned URL
                    dto.setContentUrl(
                        r2Service.generatePresignedUrl("private/" + asset.getObjectKey(), 200));
                    break;

                case APPROVED:
                case ACTIVE:
                case PAUSED:
                case COMPLETED:
                case EXPIRED:
                    // Asset público → CDN
                    dto.setContentUrl(
                        r2Service.buildPublicUrl("public/" + asset.getObjectKey())
                    );
                    break;

                case BLOCKED:
                default:
                    dto.setContentUrl(null);
            }

            return dto;
        });

        return PagedResponse.from(dtoPage);
    }

    @Override
    @Transactional(readOnly = true)
    public AdResponseDTO getAdById(Long adId) {
        Ad ad = getAdEntityById(adId);
        return adMapper.toDto(ad);
    }

    @Override
    @Transactional(readOnly = true)
    public Ad getAdEntityById(Long adId) {
        Objects.requireNonNull(adId, "El ID del anuncio no puede ser nulo");
        return adRepository.findById(adId)
            .orElseThrow(() -> new AdNotFoundException("Anuncio no encontrado con ID: " + adId));
    }

    @Override
    public AdResponseDTO activateAdAsAdvertiser(Long adId, Long advertiserId) {
        Ad ad = adRepository.findByIdAndAdvertiserId(adId, advertiserId)
            .orElseThrow(() -> new AdNotFoundException("Anuncio no encontrado"));
        
        if (ad.getStatus() != AdStatus.APPROVED && ad.getStatus() != AdStatus.PAUSED) {
            throw new InvalidAdStateException(
                "Solo se pueden activar anuncios aprobados o pausados"
            );
        }
        
        ad.setStatus(AdStatus.ACTIVE);
        ad.setUpdatedAt(ZonedDateTime.now());
        
        if (ad.getStartDate() == null) {
            ad.setStartDate(ZonedDateTime.now());
        }
        
        Ad savedAd = adRepository.save(ad);
        log.info("Ad {} activated", adId);
        
        return adMapper.toDto(savedAd);
    }

    @Override
    public AdResponseDTO pauseAdAsAdvertiser(Long adId, Long advertiserId) {
        Ad ad = adRepository.findByIdAndAdvertiserId(adId, advertiserId)
            .orElseThrow(() -> new AdNotFoundException("Anuncio no encontrado"));

        if (ad.getStatus() != AdStatus.ACTIVE) {
            throw new InvalidAdStateException(
                "Solo se pueden pausar anuncios activos"
            );
        }
        
        ad.setStatus(AdStatus.PAUSED);
        ad.setUpdatedAt(ZonedDateTime.now());
        
        Ad savedAd = adRepository.save(ad);
        log.info("Ad {} paused", adId);
        
        return adMapper.toDto(savedAd);
    }

    // ==================== Consultas para UsuariosConsumer ====================

    @Override
    @Transactional
    public Optional<AdForConsumerDTO> getNextAdForUser(Long userId) {

        log.debug("Buscando siguiente anuncio disponible para usuario: {}", userId);

        Objects.requireNonNull(userId, "El ID de usuario no puede ser nulo");

        User user = userRepository.findById(userId).orElseThrow(() ->
            new ObjectNotFoundException("Usuario con ID " + userId + " no encontrado", User.class)
        );

        ZonedDateTime now = ZonedDateTime.now(clock);

        // Reanudar sesión activa si existe
        Optional<AdWatchSession> activeSession =
            adWatchSessionRepository
                .findByUserIdAndStatusAndExpiresAtAfter(
                    userId,
                    AdWatchSessionStatus.ACTIVE,
                    now
                );

        if (activeSession.isPresent()) {
            log.info("Reanudando sesión activa {}", activeSession.get().getId());
            AdWatchSession session = activeSession.get();
            AdForConsumerDTO dto = adMapper.toConsumerDto(session.getAd());
            dto.setSessionUUID(session.getId());
            return Optional.of(dto);
        }

        Optional<Ad> adOpt = findWithCategoryMatch(userId, now)
        .or(() -> findWithoutCategoryMatch(userId, now));

        if (adOpt.isEmpty()) {
            return Optional.empty();
        }

        Ad ad = adOpt.get();
        AdWatchSession session = new AdWatchSession(user, ad);
        adWatchSessionRepository.save(session);

        // Posibilidad de fallback de nivel 2 con los anuncios vistos

        AdForConsumerDTO dto = adMapper.toConsumerDto(ad);
        dto.setSessionUUID(session.getId());

        return Optional.of(dto);
    }

    @Transactional(readOnly = true)
    public Optional<Ad> findWithCategoryMatch(Long userId, ZonedDateTime now) {
        log.info("Mostrando anuncio con coincidencia de categoría para el usuario {}", userId);
        List<Ad> ads = adRepository.findFirstAvailableAdForUser(
            userId,
            AdStatus.ACTIVE,
            List.of(AdWatchSessionStatus.LIKED),
            now,
            PageRequest.of(0, 1)
        );

        return ads.stream().findFirst();
    }

    private Optional<Ad> findWithoutCategoryMatch(Long userId, ZonedDateTime now) {
        log.info("Mostrando anuncio sin coincidencia de categoría para el usuario {}", userId);
        List<Ad> ads = adRepository.findNextAdWithoutCategoryMatch(
            userId,
            AdStatus.ACTIVE,
            List.of(AdWatchSessionStatus.LIKED),
            now,
            PageRequest.of(0, 1)
        );
        return ads.stream().findFirst();
    }

    /**
     * Cuenta los anuncios disponibles para un usuario.
     * 
     * @param userId ID del usuario
     * @return Cantidad de anuncios disponibles
     */
    @Transactional(readOnly = true)
    // @Cacheable(value = "availableAdsCount", key = "#userId")
    @Override
    public long countAvailableAdsForUser(Long userId) {
        ZonedDateTime now = ZonedDateTime.now(clock);
        return adRepository.countAvailableAdsForUser(userId, AdStatus.ACTIVE, now);
    }

    // ==================== Gestión de Estado (Admin) ====================

    @Override
    public AdResponseDTO activateAdAsAdmin(Long adId) {
        Objects.requireNonNull(adId, "El ID del anuncio no puede ser nulo");
        Ad ad = adRepository.findById(adId)
            .orElseThrow(() -> new AdNotFoundException("Anuncio no encontrado"));
        
        if (ad.getStatus() != AdStatus.APPROVED && ad.getStatus() != AdStatus.PAUSED && ad.getStatus() != AdStatus.BLOCKED) {
            throw new InvalidAdStateException(
                "Solo se pueden activar anuncios aprobados o pausados o bloqueados"
            );
        }
        
        ad.setStatus(AdStatus.ACTIVE);
        ad.setUpdatedAt(ZonedDateTime.now());
        
        if (ad.getStartDate() == null) {
            ad.setStartDate(ZonedDateTime.now());
        }
        
        Ad savedAd = adRepository.save(ad);
        log.info("Ad {} activated", adId);
        
        return adMapper.toDto(savedAd);
    }

    @Override
    public AdResponseDTO pauseAdAsAdmin(Long adId) {
        Objects.requireNonNull(adId, "El ID del anuncio no puede ser nulo");
        Ad ad = adRepository.findById(adId)
            .orElseThrow(() -> new AdNotFoundException("Anuncio no encontrado"));

        if (ad.getStatus() != AdStatus.ACTIVE && ad.getStatus() != AdStatus.BLOCKED) {
            throw new InvalidAdStateException(
                "Solo se pueden pausar anuncios activos o bloqueados"
            );
        }
        
        ad.setStatus(AdStatus.PAUSED);
        ad.setUpdatedAt(ZonedDateTime.now());
        
        Ad savedAd = adRepository.save(ad);
        log.info("Ad {} paused", adId);
        
        return adMapper.toDto(savedAd);
    }

    @Override
    public AdResponseDTO blockAdAsAdmin(Long adId) {
        Objects.requireNonNull(adId, "El ID del anuncio no puede ser nulo");
        Ad ad = adRepository.findById(adId)
            .orElseThrow(() -> new AdNotFoundException("Anuncio no encontrado"));

        if (ad.getStatus() != AdStatus.APPROVED && ad.getStatus() != AdStatus.PAUSED && ad.getStatus() != AdStatus.ACTIVE) {
            throw new InvalidAdStateException(
                "Solo se pueden bloquear anuncios activos, pausados o aprobados"
            );
        }
        
        ad.setStatus(AdStatus.BLOCKED);
        ad.setUpdatedAt(ZonedDateTime.now());
        
        Ad savedAd = adRepository.save(ad);
        log.info("Ad {} blocked", adId);
        
        return adMapper.toDto(savedAd);
    }

    @Override
    public AdResponseDTO approveAd(Long adId, Long adminId) {
        log.info("Admin {} approving ad {}", adminId, adId);
        
        Ad ad = getAdEntityById(adId);
        
        if (ad.getStatus() != AdStatus.PENDING) {
            throw new InvalidAdStateException(
                "Solo se pueden aprobar anuncios pendientes"
            );
        }
        
        ad.setStatus(AdStatus.APPROVED);
        ad.setUpdatedAt(ZonedDateTime.now());
        
        Ad savedAd = adRepository.save(ad);
        log.info("Ad {} approved successfully", adId);
        
        return adMapper.toDto(savedAd);
    }

    @Override
    public AdResponseDTO rejectAd(Long adId, String reason, Long adminId) {
        log.info("Admin {} rejecting ad {}", adminId, adId);
        
        Ad ad = getAdEntityById(adId);
        
        if (ad.getStatus() != AdStatus.PENDING) {
            throw new InvalidAdStateException(
                "Solo se pueden rechazar anuncios pendientes"
            );
        }
        
        ad.setStatus(AdStatus.REJECTED);
        ad.setRejectionReason(reason);
        ad.setUpdatedAt(ZonedDateTime.now());
        
        Ad savedAd = adRepository.save(ad);
        log.info("Ad {} rejected", adId);
        
        return adMapper.toDto(savedAd);
    }
    @Override
    @Transactional(readOnly = true)
    public Page<AdForAdminDTO> getAdsByStatus(AdStatus status, Pageable pageable) {
        Page<Ad> ads = adRepository.findAllByStatus(status, pageable);
        return ads.map(adMapper::toAdminDto);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AdForAdminDTO> getPendingApprovalAds(Pageable pageable) {
        Page<Ad> ads = adRepository.findPendingApproval(pageable);
        return ads.map(adMapper::toAdminDto);
    }

    // ==================== Estadísticas ====================

    @Override
    @Transactional(readOnly = true)
    public AdStatsDTO getAdStats(Long adId, Long advertiserId) {
        Ad ad = adRepository.findByIdAndAdvertiserId(adId, advertiserId)
            .orElseThrow(() -> new AdNotFoundException("Anuncio no encontrado"));
        
        return AdStatsDTO.builder()
            .adId(adId)
            .totalLikes(ad.getCurrentLikes())
            .maxLikes(ad.getMaxLikes())
            .remainingLikes(ad.getRemainingLikes())
            .completionPercentage(ad.getCompletionPercentage())
            .totalBudget(ad.getTotalBudget())
            .spentBudget(ad.getSpentBudget())
            .remainingBudget(ad.getRemainingBudget())
            .rewardPerLike(ad.getRewardPerLike())
            .status(ad.getStatus())
            .createdAt(ad.getCreatedAt())
            .startDate(ad.getStartDate())
            .endDate(ad.getEndDate())
            .build();
    }

    @Override
    @Transactional(readOnly = true)
    public AdStatsDTO getAdvertiserStats(Long advertiserId) {
        Long totalAds = countAdsByAdvertiser(advertiserId);
        Long activeAds = countAdsByAdvertiserAndStatus(advertiserId, AdStatus.ACTIVE);
        // BigDecimal totalSpent = getTotalSpentByAdvertiser(advertiserId);
        Long totalLikes = getTotalLikesByAdvertiser(advertiserId);
        
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

    // ==================== Tareas Programadas ====================

    @Override
    public void autoDeactivateCompletedAds() {
        log.info("Running auto-deactivation task for completed ads");
        
        List<Ad> adsToDeactivate = adRepository.findAdsToAutoDeactivate(ZonedDateTime.now());
        
        if (adsToDeactivate.isEmpty()) {
            log.info("No ads to deactivate");
            return;
        }
        
        for (Ad ad : adsToDeactivate) {
            ad.setStatus(AdStatus.COMPLETED);
            ad.setUpdatedAt(ZonedDateTime.now());
            adRepository.save(ad);
            
            log.info("Ad {} auto-deactivated. Reason: {} likes of {}, Budget: {} of {}",
                ad.getId(), ad.getCurrentLikes(), ad.getMaxLikes(),
                ad.getSpentBudget(), ad.getTotalBudget());
        }
        
        log.info("Auto-deactivation completed. Total ads deactivated: {}", 
            adsToDeactivate.size());
    }

    @Override
    public void checkExpiredAds() {
        log.info("Checking for expired ads");
        
        List<Ad> expiredAds = adRepository.findAdsToAutoDeactivate(ZonedDateTime.now());
        
        for (Ad ad : expiredAds) {
            if (ad.getEndDate() != null && ad.getEndDate().isBefore(ZonedDateTime.now())) {
                ad.setStatus(AdStatus.EXPIRED);
                ad.setUpdatedAt(ZonedDateTime.now());
                adRepository.save(ad);
                
                log.info("Ad {} marked as expired", ad.getId());
            }
        }
    }

    // ==================== Validaciones ====================

    @Override
    public void validateAdBudget(Long adId) {
        Ad ad = getAdEntityById(adId);
        
        if (!ad.hasRemainingBudget()) {
            ad.setStatus(AdStatus.COMPLETED);
            adRepository.save(ad);
            
            throw new InsufficientBudgetException(
                "El anuncio ha agotado su presupuesto"
            );
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
    public Long countAdsByAdvertiser(Long advertiserId) {
        return adRepository.countByAdvertiserId(advertiserId);
    }

    @Override
    @Transactional(readOnly = true)
    public Long countAdsByAdvertiserAndStatus(Long advertiserId, AdStatus status) {
        return adRepository.countByAdvertiserIdAndStatus(advertiserId, status);
    }

    // @Override
    // @Transactional(readOnly = true)
    // public BigDecimal getTotalSpentByAdvertiser(Long advertiserId) {
    //     BigDecimal total = adRepository.sumSpentBudgetByAdvertiserId(advertiserId);
    //     return total != null ? total : BigDecimal.ZERO;
    // }

    @Override
    @Transactional(readOnly = true)
    public Long getTotalLikesByAdvertiser(Long advertiserId) {
        Long total = adRepository.sumLikesByAdvertiserId(advertiserId);
        return total != null ? total : 0L;
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
                    mediaType, maxSize / 1024 / 1024)
            );
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
    private String generateAdAssetObjectKey(Long advertiserId, FileUploadRequestDTO metadata) {
        String timestamp = String.valueOf(System.currentTimeMillis());
        String uuid = UUID.randomUUID().toString().substring(0, 8);
        String extension = getFileExtension(metadata.getOriginalFileName());
        
        return String.format("ads/advertiser-%d/%s-%s%s", 
            advertiserId, timestamp, uuid, extension);
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
                SupportedMimeType.IMAGE_WEBP
            );
            case VIDEO -> Set.of(
                SupportedMimeType.VIDEO_MP4,
                SupportedMimeType.VIDEO_QUICK_TIME
            );
            default -> throw new ValidationException("Media type no soportado: " + mediaType);
        };
    }

    /**
     * Obtener tamaño máximo según tipo de media
     */
    private long getMaxSizeBytesForMedia(MediaType mediaType) {
        return switch (mediaType) {
            case IMAGE -> 5 * 1024 * 1024;      // 5 MB
            case VIDEO -> 100 * 1024 * 1024;    // 100 MB
            default -> throw new ValidationException("Media type no soportado: " + mediaType);
        };
    }

    private Double resolveAssetDuration(AdAsset asset) {

        return switch (asset.getMediaType()) {

            case IMAGE -> 6.0; // regla de negocio fija

            case VIDEO -> {
                Double duration = mediaMetadataService
                    .getVideoDurationSeconds(asset.getObjectKey());

                if (duration < 5 || duration > 30) {
                    throw new ValidationException(
                        "Duración de video no permitida: " + duration + "s"
                    );
                }

                yield duration;
            }
            default -> throw new IllegalArgumentException("Unexpected value: " + asset.getMediaType());
        };
    }
}