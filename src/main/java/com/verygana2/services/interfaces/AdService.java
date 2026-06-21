package com.verygana2.services.interfaces;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

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
import com.verygana2.models.ads.Ad;
import com.verygana2.models.enums.AdStatus;

public interface AdService {
    
    // Para commercials
    AssetAnalysisResultDTO analyzeAsset(Long assetId, Long commercialId);

    AssetOrphanedResponseDTO markAssetAsOrphaned(Long commercialId, Long assetId);

    void createAdWithAsset(Long commercialId, CreateAdRequestDTO request);

    AdAssetUploadPermissionDTO prepareAdAssetUpload(Long commercialId, FileUploadRequestDTO request);
    
    AdResponseDTO updateAd(Long adId, AdUpdateDTO updateDto, Long commercialId);

    PagedResponse<AdResponseDTO> getFilteredAds(Long commercialId, AdFilterDTO filters, Pageable pageable);

    AdResponseDTO getAdDetails(Long adId, Long commercialId);

    Ad getAdEntityById(Long adId);

    AdResponseDTO activateAdAsCommercial(Long adId, Long commercialId);
        
    AdResponseDTO pauseAdAsCommercial(Long adId, Long commercialId);
    
    // Gestión de estado (Admin)
    AdResponseDTO activateAdAsAdmin(Long adId);
        
    AdResponseDTO pauseAdAsAdmin(Long adId);

    AdResponseDTO blockAdAsAdmin(Long adId);

    AdResponseDTO approveAd(Long adId, Long adminId);
    
    AdResponseDTO rejectAd(Long adId, String reason, Long adminId);

    Page<AdForAdminDTO> getAdsByStatus(AdStatus status, Pageable pageable);
    
    // Estadísticas
    AdStatsDTO getAdStats(Long adId, Long commercialId);
    
    AdStatsDTO getCommercialStats(Long commercialId);
    
    Page<AdResponseDTO> getTopAdsByLikes(Pageable pageable);
    
    // Validaciones
    void validateAdBudget(Long adId);
    
    boolean canAdReceiveLike(Long adId);
    
    // Utilidades
    Long countAdsByCommercial(Long commercialId);
    
    Long countAdsByCommercialAndStatus(Long commercialId, AdStatus status);
    
    // BigDecimal getTotalSpentByCommercial(Long commercialId);
    
    Long getTotalLikesByCommercial(Long commercialId);
}
