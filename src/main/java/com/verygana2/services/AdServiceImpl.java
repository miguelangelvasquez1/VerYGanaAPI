package com.verygana2.services;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.verygana2.dtos.ad.requests.AdCreateDTO;
import com.verygana2.dtos.ad.requests.AdUpdateDTO;
import com.verygana2.dtos.ad.responses.AdResponseDTO;
import com.verygana2.dtos.ad.responses.AdStatsDTO;
import com.verygana2.exceptions.adsExceptions.AdNotFoundException;
import com.verygana2.exceptions.adsExceptions.DuplicateLikeException;
import com.verygana2.exceptions.adsExceptions.InsufficientBudgetException;
import com.verygana2.exceptions.adsExceptions.InvalidAdStateException;
import com.verygana2.mappers.AdMapper;
import com.verygana2.models.Category;
import com.verygana2.models.Transaction;
import com.verygana2.models.User;
import com.verygana2.models.ads.Ad;
import com.verygana2.models.ads.AdLike;
import com.verygana2.models.ads.AdLikeId;
import com.verygana2.models.enums.AdStatus;
import com.verygana2.models.enums.TransactionState;
import com.verygana2.models.enums.TransactionType;
import com.verygana2.models.userDetails.AdvertiserDetails;
import com.verygana2.repositories.AdLikeRepository;
import com.verygana2.repositories.AdRepository;
import com.verygana2.repositories.TransactionRepository;
import com.verygana2.repositories.UserRepository;
import com.verygana2.services.interfaces.AdService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class AdServiceImpl implements AdService {
    
    private final AdRepository adRepository;
    private final AdLikeRepository adLikeRepository;
    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;
    private final AdMapper adMapper;

    // ==================== CRUD Básico ====================

    @Override
    public AdResponseDTO createAd(AdCreateDTO createDto, Long advertiserId) {
        log.info("Creating ad for advertiser: {}", advertiserId);
        
        User advertiser = userRepository.findById(advertiserId)
            .orElseThrow(() -> new AdNotFoundException("Advertiser not found"));
        
        // Validar que no exista un anuncio activo con el mismo título
        if (adRepository.existsByAdvertiserIdAndTitle(advertiserId, createDto.getTitle())) {
            throw new InvalidAdStateException("Ya existe un anuncio activo con ese título");
        }
        
        // Validar presupuesto
        BigDecimal minimumBudget = createDto.getRewardPerLike()
            .multiply(BigDecimal.valueOf(createDto.getMaxLikes()));
        
        if (createDto.getTotalBudget().compareTo(minimumBudget) < 0) {
            throw new InsufficientBudgetException(
                "El presupuesto total debe ser al menos: " + minimumBudget
            );
        }
        
        Ad ad = adMapper.toEntity(createDto);
        ad.setAdvertiser((AdvertiserDetails)advertiser.getUserDetails());
        ad.setCreatedAt(LocalDateTime.now());
        ad.setStatus(AdStatus.PENDING);
        ad.setCurrentLikes(0);
        ad.setSpentBudget(BigDecimal.ZERO);
        
        Ad savedAd = adRepository.save(ad);
        log.info("Ad created successfully with ID: {}", savedAd.getId());
        
        return adMapper.toDto(savedAd);
    }

    @Override
    public AdResponseDTO updateAd(Long adId, AdUpdateDTO updateDto, Long advertiserId) {
        log.info("Updating ad {} for advertiser {}", adId, advertiserId);
        
        Ad ad = adRepository.findByIdAndAdvertiserId(adId, advertiserId)
            .orElseThrow(() -> new AdNotFoundException("Anuncio no encontrado"));
        
        // No permitir editar si ya está en progreso
        if (ad.getCurrentLikes() > 0) {
            throw new InvalidAdStateException(
                "No se puede editar un anuncio que ya tiene likes"
            );
        }
        
        adMapper.updateEntityFromDto(updateDto, ad);
        ad.setUpdatedAt(LocalDateTime.now());
        
        Ad updatedAd = adRepository.save(ad);
        return adMapper.toDto(updatedAd);
    }

    @Override
    public void deleteAd(Long adId, Long advertiserId) {
        log.info("Deleting ad {} for advertiser {}", adId, advertiserId);
        
        Ad ad = adRepository.findByIdAndAdvertiserId(adId, advertiserId)
            .orElseThrow(() -> new AdNotFoundException("Anuncio no encontrado"));
        
        // Solo permitir eliminar si no tiene likes
        if (ad.getCurrentLikes() > 0) {
            throw new InvalidAdStateException(
                "No se puede eliminar un anuncio que ya tiene likes. Desactívalo en su lugar."
            );
        }
        
        adRepository.delete(ad);
        log.info("Ad deleted successfully");
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
        return adRepository.findById(adId)
            .orElseThrow(() -> new AdNotFoundException("Anuncio no encontrado con ID: " + adId));
    }

    // ==================== Consultas para Anunciantes ====================

    @Override
    @Transactional(readOnly = true)
    public Page<AdResponseDTO> getAdsByAdvertiser(Long advertiserId, Pageable pageable) {
        Page<Ad> ads = adRepository.findByAdvertiserId(advertiserId, pageable);
        return ads.map(adMapper::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AdResponseDTO> getAdsByAdvertiserAndStatus(
            Long advertiserId, AdStatus status, Pageable pageable) {
        
        Page<Ad> ads = adRepository.findByAdvertiserId(advertiserId, pageable);
        return ads.map(adMapper::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AdResponseDTO> searchAdvertiserAds(
            Long advertiserId, String searchTerm, Pageable pageable) {
        
        Page<Ad> ads = adRepository.searchByAdvertiser(advertiserId, searchTerm, pageable);
        return ads.map(adMapper::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AdResponseDTO> getAdsByAdvertiserAndDateRange(
            Long advertiserId, LocalDateTime startDate, LocalDateTime endDate) {
        
        List<Ad> ads = adRepository.findByAdvertiserIdAndDateRange(
            advertiserId, startDate, endDate
        );
        return ads.stream()
            .map(adMapper::toDto)
            .collect(Collectors.toList());
    }

    // ==================== Consultas para Usuarios ====================

    @Override
    @Transactional(readOnly = true)
    public Page<AdResponseDTO> getAvailableAds(Pageable pageable) {
        Page<Ad> ads = adRepository.findAvailableAds(LocalDateTime.now(), pageable);
        return ads.map(adMapper::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AdResponseDTO> getAvailableAdsByCategory(
            List<Category> categories, Pageable pageable) {
        
        Page<Ad> ads = adRepository.findAvailableAdsByCategories(
            categories, LocalDateTime.now(), pageable
        );
        return ads.map(adMapper::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AdResponseDTO> getAvailableAdsForUser(Long userId, Pageable pageable) {
        Page<Ad> ads = adRepository.findAvailableAdsNotSeenByUser(
            userId, LocalDateTime.now(), pageable
        );
        return ads.map(adMapper::toDto);
    }

    // ==================== Acciones sobre Anuncios ====================

    @Override
    public AdResponseDTO activateAd(Long adId, Long advertiserId) {
        Ad ad = adRepository.findByIdAndAdvertiserId(adId, advertiserId)
            .orElseThrow(() -> new AdNotFoundException("Anuncio no encontrado"));
        
        if (ad.getStatus() != AdStatus.APPROVED) {
            throw new InvalidAdStateException(
                "Solo se pueden activar anuncios aprobados"
            );
        }
        
        ad.setStatus(AdStatus.ACTIVE);
        ad.setUpdatedAt(LocalDateTime.now());
        
        if (ad.getStartDate() == null) {
            ad.setStartDate(LocalDateTime.now());
        }
        
        Ad savedAd = adRepository.save(ad);
        log.info("Ad {} activated", adId);
        
        return adMapper.toDto(savedAd);
    }

    @Override
    public AdResponseDTO deactivateAd(Long adId, Long advertiserId) {
        Ad ad = adRepository.findByIdAndAdvertiserId(adId, advertiserId)
            .orElseThrow(() -> new AdNotFoundException("Anuncio no encontrado"));
        
        ad.setUpdatedAt(LocalDateTime.now());
        
        Ad savedAd = adRepository.save(ad);
        log.info("Ad {} deactivated", adId);
        
        return adMapper.toDto(savedAd);
    }

    @Override
    public AdResponseDTO pauseAd(Long adId, Long advertiserId) {
        Ad ad = adRepository.findByIdAndAdvertiserId(adId, advertiserId)
            .orElseThrow(() -> new AdNotFoundException("Anuncio no encontrado"));
        
        ad.setStatus(AdStatus.PAUSED);
        ad.setUpdatedAt(LocalDateTime.now());
        
        Ad savedAd = adRepository.save(ad);
        log.info("Ad {} paused", adId);
        
        return adMapper.toDto(savedAd);
    }

    @Override
    public AdResponseDTO resumeAd(Long adId, Long advertiserId) {
        Ad ad = adRepository.findByIdAndAdvertiserId(adId, advertiserId)
            .orElseThrow(() -> new AdNotFoundException("Anuncio no encontrado"));
        
        if (ad.getStatus() != AdStatus.PAUSED) {
            throw new InvalidAdStateException("Solo se pueden reanudar anuncios pausados");
        }
        
        ad.setStatus(AdStatus.ACTIVE);
        ad.setUpdatedAt(LocalDateTime.now());
        
        Ad savedAd = adRepository.save(ad);
        log.info("Ad {} resumed", adId);
        
        return adMapper.toDto(savedAd);
    }

    // ==================== Procesamiento de Likes ====================

    @Override
    public AdResponseDTO processAdLike(
            Long adId, Long userId, String ipAddress, String userAgent) {
        
        log.info("Processing like for ad {} from user {}", adId, userId);
        
        // Verificar que el usuario existe
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new AdNotFoundException("Usuario no encontrado"));
        
        // Verificar que el anuncio existe
        Ad ad = getAdEntityById(adId);
        
        // Verificar que el usuario no haya dado like antes
        if (hasUserLikedAd(adId, userId)) {
            throw new DuplicateLikeException("Ya has dado like a este anuncio");
        }
        
        // Verificar que el anuncio puede recibir likes
        if (!ad.canReceiveLike()) {
            throw new InvalidAdStateException(
                "Este anuncio no está disponible para recibir likes"
            );
        }
        
        // Crear el like
        AdLike adLike = AdLike.builder()
            .id(new AdLikeId(userId, adId))
            .user(user)
            .ad(ad)
            .rewardAmount(ad.getRewardPerLike())
            .createdAt(LocalDateTime.now())
            .build();
        
        // Crear la transacción
        Transaction transaction = Transaction.builder()
            .walletId(user.getWallet().getId())
            .amount(ad.getRewardPerLike())
            .transactionType(TransactionType.POINTS_AD_LIKE_REWARD)
            .transactionState(TransactionState.COMPLETED)
            .createdAt(LocalDateTime.now())
            .completedAt(LocalDateTime.now())
            .build();
        
        transaction = transactionRepository.save(transaction);
        
        // Guardar el like
        adLikeRepository.save(adLike);
        
        // Actualizar el anuncio
        ad.incrementLike(ad.getRewardPerLike());
        Ad savedAd = adRepository.save(ad);
        
        // Actualizar el balance del usuario
        user.getWallet().setBalance(user.getWallet().getBalance().add(ad.getRewardPerLike()));
        userRepository.save(user);
        
        log.info("Like processed successfully. User rewarded with: {}", 
            ad.getRewardPerLike());
        
        return adMapper.toDto(savedAd);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasUserLikedAd(Long adId, Long userId) {
        return adRepository.hasUserLikedAd(adId, userId);
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
        ad.setUpdatedAt(LocalDateTime.now());
        
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
        ad.setUpdatedAt(LocalDateTime.now());
        
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
        BigDecimal totalSpent = getTotalSpentByAdvertiser(advertiserId);
        Long totalLikes = getTotalLikesByAdvertiser(advertiserId);
        
        return AdStatsDTO.builder()
            .totalAds(totalAds.intValue())
            .activeAds(activeAds.intValue())
            .totalSpent(totalSpent)
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
        
        List<Ad> adsToDeactivate = adRepository.findAdsToAutoDeactivate(LocalDateTime.now());
        
        if (adsToDeactivate.isEmpty()) {
            log.info("No ads to deactivate");
            return;
        }
        
        for (Ad ad : adsToDeactivate) {
            ad.setStatus(AdStatus.COMPLETED);
            ad.setUpdatedAt(LocalDateTime.now());
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
        
        List<Ad> expiredAds = adRepository.findAdsToAutoDeactivate(LocalDateTime.now());
        
        for (Ad ad : expiredAds) {
            if (ad.getEndDate() != null && ad.getEndDate().isBefore(LocalDateTime.now())) {
                ad.setStatus(AdStatus.EXPIRED);
                ad.setUpdatedAt(LocalDateTime.now());
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

    @Override
    @Transactional(readOnly = true)
    public BigDecimal getTotalSpentByAdvertiser(Long advertiserId) {
        BigDecimal total = adRepository.sumSpentBudgetByAdvertiserId(advertiserId);
        return total != null ? total : BigDecimal.ZERO;
    }

    @Override
    @Transactional(readOnly = true)
    public Long getTotalLikesByAdvertiser(Long advertiserId) {
        Long total = adRepository.sumLikesByAdvertiserId(advertiserId);
        return total != null ? total : 0L;
    }
}
