package com.verygana2.services.interfaces;

import java.util.UUID;

import com.verygana2.dtos.ad.responses.AdLikedResponse;

public interface AdLikeService {
    
    AdLikedResponse processAdLike(UUID sessionId, Long adId, Long consumerId, String ipAddress);

    boolean hasConsumerLikedAd(Long adId, Long consumerId);
}
