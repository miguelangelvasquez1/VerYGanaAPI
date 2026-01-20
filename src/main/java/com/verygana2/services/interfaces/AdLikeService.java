package com.verygana2.services.interfaces;

import java.util.UUID;

import com.verygana2.dtos.ad.responses.AdLikedResponse;

public interface AdLikeService {
    
    AdLikedResponse processAdLike(UUID sessionId, Long adId, Long userId, String ipAddress);

    boolean hasUserLikedAd(Long adId, Long userId);
}
