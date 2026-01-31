package com.verygana2.services.interfaces;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import com.verygana2.dtos.FileUploadRequestDTO;
import com.verygana2.dtos.PagedResponse;
import com.verygana2.dtos.ad.requests.AdCreateDTO;
import com.verygana2.dtos.ad.requests.AdFilterDTO;
import com.verygana2.dtos.ad.requests.AdUpdateDTO;
import com.verygana2.dtos.ad.requests.CreateAdRequestDTO;
import com.verygana2.dtos.ad.responses.AdAssetUploadPermissionDTO;
import com.verygana2.dtos.ad.responses.AdForAdminDTO;
import com.verygana2.dtos.ad.responses.AdForConsumerDTO;
import com.verygana2.dtos.ad.responses.AdResponseDTO;
import com.verygana2.dtos.ad.responses.AdStatsDTO;
import com.verygana2.models.ads.Ad;
import com.verygana2.models.enums.AdStatus;

public interface AdService {
    
    // Para advertisers
    void createAdWithAsset(Long advertiserId, CreateAdRequestDTO request);

    AdAssetUploadPermissionDTO prepareAdAssetUpload(Long advertiserId, FileUploadRequestDTO request);

    AdResponseDTO createAd(AdCreateDTO createDto, MultipartFile file, Long advertiserId);
    
    AdResponseDTO updateAd(Long adId, AdUpdateDTO updateDto, MultipartFile file, Long advertiserId);

    PagedResponse<AdResponseDTO> getFilteredAds(Long advertiserId, AdFilterDTO filters, Pageable pageable);
    
    AdResponseDTO getAdById(Long adId);

    Ad getAdEntityById(Long adId);

    AdResponseDTO activateAdAsAdvertiser(Long adId, Long advertiserId);
        
    AdResponseDTO pauseAdAsAdvertiser(Long adId, Long advertiserId);
    
    // Consultas para consumers
    Optional<AdForConsumerDTO> getNextAdForUser(Long userId);

    long countAvailableAdsForUser(Long userId);
    
    // Gestión de estado (Admin)
    AdResponseDTO activateAdAsAdmin(Long adId);
        
    AdResponseDTO pauseAdAsAdmin(Long adId);

    AdResponseDTO blockAdAsAdmin(Long adId);

    AdResponseDTO approveAd(Long adId, Long adminId);
    
    AdResponseDTO rejectAd(Long adId, String reason, Long adminId);

    Page<AdForAdminDTO> getAdsByStatus(AdStatus status, Pageable pageable);
    
    Page<AdForAdminDTO> getPendingApprovalAds(Pageable pageable);
    
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
