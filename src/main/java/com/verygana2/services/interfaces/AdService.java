package com.verygana2.services.interfaces;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.verygana2.dtos.ad.requests.AdCreateDTO;
import com.verygana2.dtos.ad.requests.AdUpdateDTO;
import com.verygana2.dtos.ad.responses.AdResponseDTO;
import com.verygana2.dtos.ad.responses.AdStatsDTO;
import com.verygana2.models.ads.Ad;
import com.verygana2.models.enums.AdStatus;
import com.verygana2.models.enums.Preference;

public interface AdService {
    
    // CRUD básico
    AdResponseDTO createAd(AdCreateDTO createDto, Long advertiserId);
    
    AdResponseDTO updateAd(Long adId, AdUpdateDTO updateDto, Long advertiserId);
    
    void deleteAd(Long adId, Long advertiserId);
    
    AdResponseDTO getAdById(Long adId);
    
    Ad getAdEntityById(Long adId);
    
    // Consultas para anunciantes
    Page<AdResponseDTO> getAdsByAdvertiser(Long advertiserId, Pageable pageable);
    
    Page<AdResponseDTO> getAdsByAdvertiserAndStatus(
        Long advertiserId, 
        AdStatus status, 
        Pageable pageable
    );
    
    Page<AdResponseDTO> searchAdvertiserAds(
        Long advertiserId, 
        String searchTerm, 
        Pageable pageable
    );
    
    List<AdResponseDTO> getAdsByAdvertiserAndDateRange(
        Long advertiserId, 
        LocalDateTime startDate, 
        LocalDateTime endDate
    );
    
    // Consultas para usuarios (ver anuncios disponibles)
    Page<AdResponseDTO> getAvailableAds(Pageable pageable);
    
    Page<AdResponseDTO> getAvailableAdsByCategory(Preference category, Pageable pageable);
    
    Page<AdResponseDTO> getAvailableAdsForUser(Long userId, Pageable pageable);
    
    // Acciones sobre anuncios
    AdResponseDTO activateAd(Long adId, Long advertiserId);
    
    AdResponseDTO deactivateAd(Long adId, Long advertiserId);
    
    AdResponseDTO pauseAd(Long adId, Long advertiserId);
    
    AdResponseDTO resumeAd(Long adId, Long advertiserId);
    
    // Procesamiento de likes
    AdResponseDTO processAdLike(Long adId, Long userId, String ipAddress, String userAgent);
    
    boolean hasUserLikedAd(Long adId, Long userId);
    
    // Gestión de estado (Admin)
    AdResponseDTO approveAd(Long adId, Long adminId);
    
    AdResponseDTO rejectAd(Long adId, String reason, Long adminId);
    
    Page<AdResponseDTO> getPendingApprovalAds(Pageable pageable);
    
    // Estadísticas
    AdStatsDTO getAdStats(Long adId, Long advertiserId);
    
    AdStatsDTO getAdvertiserStats(Long advertiserId);
    
    Page<AdResponseDTO> getTopAdsByLikes(Pageable pageable);
    
    // Tareas programadas
    void autoDeactivateCompletedAds();
    
    void checkExpiredAds();
    
    // Validaciones
    void validateAdBudget(Long adId);
    
    boolean canAdReceiveLike(Long adId);
    
    // Utilidades
    Long countAdsByAdvertiser(Long advertiserId);
    
    Long countAdsByAdvertiserAndStatus(Long advertiserId, AdStatus status);
    
    BigDecimal getTotalSpentByAdvertiser(Long advertiserId);
    
    Long getTotalLikesByAdvertiser(Long advertiserId);
}
