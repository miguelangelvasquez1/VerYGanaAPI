package com.verygana2.services;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;

import org.hibernate.ObjectNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.verygana2.dtos.PagedResponse;
import com.verygana2.dtos.ad.requests.AdCreateDTO;
import com.verygana2.dtos.ad.requests.AdFilterDTO;
import com.verygana2.dtos.ad.requests.AdUpdateDTO;
import com.verygana2.dtos.ad.responses.AdResponseDTO;
import com.verygana2.dtos.ad.responses.AdStatsDTO;
import com.verygana2.exceptions.adsExceptions.AdNotFoundException;
import com.verygana2.exceptions.adsExceptions.InsufficientBudgetException;
import com.verygana2.exceptions.adsExceptions.InvalidAdStateException;
import com.verygana2.mappers.AdMapper;
import com.verygana2.models.Category;
import com.verygana2.models.User;
import com.verygana2.models.ads.Ad;
import com.verygana2.models.enums.AdStatus;
import com.verygana2.models.userDetails.AdvertiserDetails;
import com.verygana2.repositories.AdRepository;
import com.verygana2.repositories.UserRepository;
import com.verygana2.services.interfaces.AdService;
import com.verygana2.services.interfaces.CategoryService;
import com.verygana2.utils.DateValidator;
import com.verygana2.utils.specifications.AdSpecifications;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
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

    // ==================== Consultas para Anunciantes ====================

    @Override
    public AdResponseDTO createAd(AdCreateDTO createDto, Long advertiserId) {
        log.info("Creating ad for advertiser: {}", advertiserId);

        DateValidator.validateStartBeforeEnd(createDto.getStartDate(), createDto.getEndDate(),
    "La fecha de fin debe ser posterior a la de inicio");

        // Validar que no exista un anuncio activo con el mismo título
        if (adRepository.existsByAdvertiserIdAndTitle(advertiserId, createDto.getTitle())) {
            throw new InvalidAdStateException("Ya existe un anuncio activo con ese título");
        }

        List<Category> selectedCategories = categoryService.getValidatedCategories(createDto.getCategoryIds());
        
        Ad ad = adMapper.toEntity(createDto);
        ad.setAdvertiser(entityManager.getReference(AdvertiserDetails.class, advertiserId));
        ad.setCategories(selectedCategories);

        Ad savedAd = adRepository.save(ad);
        log.info("Ad created successfully with ID: {}", savedAd.getId());
        
        return adMapper.toDto(savedAd);
    }

    @Override
    public AdResponseDTO updateAd(Long adId, AdUpdateDTO updateDto, Long advertiserId) {
        log.info("Updating ad {} for advertiser {}", adId, advertiserId);

        DateValidator.validateStartBeforeEnd(updateDto.getStartDate(), updateDto.getEndDate(),
    "La fecha de fin debe ser posterior a la de inicio");
        
        Ad ad = adRepository.findByIdAndAdvertiserId(adId, advertiserId)
            .orElseThrow(() -> new AdNotFoundException("Anuncio no encontrado"));
        
        // No permitir editar si ya está ACTIVE
        if (ad.getStatus() != AdStatus.PENDING) {
            throw new InvalidAdStateException("Solo se pueden editar anuncios pendientes de aprobación");
        }
        
        List<Category> selectedCategories = categoryService.getValidatedCategories(updateDto.getCategoryIds());

        adMapper.updateEntityFromDto(updateDto, ad);
        ad.setCategories(selectedCategories);
        
        Ad updatedAd = adRepository.save(ad);
        log.info("Ad {} updated successfully by advertiser {}", adId, advertiserId);
        return adMapper.toDto(updatedAd);
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<AdResponseDTO> getFilteredAds(Long advertiserId, AdFilterDTO filters, Pageable pageable) {
        Specification<Ad> spec = AdSpecifications.hasAdvertiser(advertiserId)
            .and(AdSpecifications.hasStatus(filters.getStatus()))
            .and(AdSpecifications.hasSearchTerm(filters.getSearchTerm()))
            .and(AdSpecifications.inDateRange(filters.getStartDate(), filters.getEndDate()))
            .and(AdSpecifications.inCategories(filters.getCategoryIds()));

        Page<Ad> ads = adRepository.findAll(spec, Objects.requireNonNull(pageable));
        return PagedResponse.from(ads.map(adMapper::toDto));
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
    public AdResponseDTO activateAd(Long adId, Long advertiserId) {
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
    public AdResponseDTO pauseAd(Long adId, Long advertiserId) {
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

    /**
     * Obtiene anuncios disponibles para un usuario con filtrado por categorías.
     * Los resultados se ordenan por fecha de creación (más recientes primero).
     * 
     * @param userId ID del usuario
     * @param pageable Parámetros de paginación
     * @return Página de DTOs de anuncios disponibles
     * @throws UserNotFoundException si el usuario no existe
     */
    @Transactional(readOnly = true)
    public PagedResponse<AdResponseDTO> getAvailableAdsForUser(Long userId, Pageable pageable) {
        log.debug("Buscando anuncios disponibles para usuario: {}", userId);

        // Validar que el usuario existe (se puede evitar al buscar en la bd al hacer login)
        Objects.requireNonNull(userId, "El ID de usuario no puede ser nulo");
        if (!userRepository.existsById(userId)) {
            log.error("Usuario no encontrado: {}", userId);
            throw new ObjectNotFoundException("Usuario con ID " + userId + " no encontrado", User.class);
        }

        ZonedDateTime now = ZonedDateTime.now();
        
        // Intentar obtener anuncios con filtro de categorías
        Page<Ad> adsPage = adRepository.findAvailableAdsForUser(
            userId,
            AdStatus.ACTIVE,
            now,
            pageable
        );

        // Si no hay resultados con categorías, intentar sin filtro de categorías
        if (adsPage.isEmpty() && pageable.getPageNumber() == 0) {
            log.debug("No se encontraron anuncios con filtro de categorías, intentando sin filtro");
            adsPage = adRepository.findAvailableAdsForUserWithoutCategoryFilter(
                userId,
                AdStatus.ACTIVE,
                now,
                pageable
            );
        }

        log.info("Se encontraron {} anuncios disponibles para usuario {}", 
                 adsPage.getTotalElements(), userId);

        return PagedResponse.from(adsPage.map(adMapper::toDto));
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
        ZonedDateTime now = ZonedDateTime.now();
        return adRepository.countAvailableAdsForUser(userId, AdStatus.ACTIVE, now);
    }

    // ==================== Gestión de Estado (Admin) ====================

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
    public Page<AdResponseDTO> getPendingApprovalAds(Pageable pageable) {
        Page<Ad> ads = adRepository.findPendingApproval(pageable);
        return ads.map(adMapper::toDto);
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
}