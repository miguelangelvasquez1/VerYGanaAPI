package com.verygana2.services.interfaces;

public interface AdLikeService {
    
    boolean processAdLike(Long adId, Long userId, String ipAddress);

    boolean hasUserLikedAd(Long adId, Long userId);
}
