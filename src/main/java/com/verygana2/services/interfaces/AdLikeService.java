package com.verygana2.services.interfaces;

import java.util.UUID;

import org.springframework.data.domain.Pageable;

import com.verygana2.dtos.PagedResponse;
import com.verygana2.dtos.ad.responses.AdLikeResponseDTO;
import com.verygana2.dtos.ad.responses.AdLikedResponse;
import com.verygana2.dtos.ad.responses.AdResponseDTO;

public interface AdLikeService {
    
    AdLikedResponse processAdLike(UUID sessionId, Long adId, Long consumerId, String ipAddress);

    boolean hasConsumerLikedAd(Long adId, Long consumerId);

    AdResponseDTO getAdDetails(Long adId, Long commercialId);

    PagedResponse<AdLikeResponseDTO> getAdLikes(Long adId, Long commercialId, Pageable pageable);
}
