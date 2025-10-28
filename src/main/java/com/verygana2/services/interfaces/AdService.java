package com.verygana2.services.interfaces;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.verygana2.dtos.PagedResponse;
import com.verygana2.dtos.ad.requests.AdCreateDTO;
import com.verygana2.dtos.ad.requests.AdFilterDTO;
import com.verygana2.dtos.ad.requests.AdUpdateDTO;
import com.verygana2.dtos.ad.responses.AdResponseDTO;
import com.verygana2.dtos.ad.responses.AdStatsDTO;
import com.verygana2.models.ads.Ad;
import com.verygana2.models.enums.AdStatus;

public interface AdService {
    
    // Para advertisers
    AdResponseDTO createAd(AdCreateDTO createDto, Long advertiserId);
    
    AdResponseDTO updateAd(Long adId, AdUpdateDTO updateDto, Long advertiserId);

    PagedResponse<AdResponseDTO> getFilteredAds(Long advertiserId, AdFilterDTO filters, Pageable pageable);
    
    AdResponseDTO getAdById(Long adId);

    AdResponseDTO activateAd(Long adId, Long advertiserId);
        
    AdResponseDTO pauseAd(Long adId, Long advertiserId);
    
    Ad getAdEntityById(Long adId);
    
    // Consultas para consumers
    PagedResponse<AdResponseDTO> getAvailableAdsForUser(Long userId, Pageable pageable);

    long countAvailableAdsForUser(Long userId);
    
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
    
    // BigDecimal getTotalSpentByAdvertiser(Long advertiserId);
    
    Long getTotalLikesByAdvertiser(Long advertiserId);
}
